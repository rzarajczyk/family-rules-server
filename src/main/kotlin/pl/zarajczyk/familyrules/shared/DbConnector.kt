package pl.zarajczyk.familyrules.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.zarajczyk.familyrules.gui.bff.SchedulePacker
import java.util.*
import kotlin.Throws

@Service
@Transactional
class DbConnector(private val schedulePacker: SchedulePacker) {

    object Users : Table() {
        val username: Column<String> = text("username")
        val passwordSha256: Column<String> = text("password_sha256")

        override val primaryKey = PrimaryKey(username)
    }

    object Instances : Table() {
        val instanceId: Column<UUID> = uuid("instance_id")
        val username: Column<String> = text("username") references Users.username
        val instanceName: Column<String> = text("instance_name")
        val instanceTokenSha256: Column<String> = text("instance_token_sha256")
        val clientType: Column<String> = text("client_type")
        val forcedDeviceState: Column<DeviceState?> =
            text("forced_device_state").nullable() //(text("forced_device_state") references DeviceStates.deviceState).nullable()
        val clientVersion: Column<String> = text("client_version")
        val clientTimezoneOffsetSeconds: Column<Int> = integer("client_timezone_offset_seconds")
        val schedule: Column<WeeklyScheduleDto> = jsonb<WeeklyScheduleDto>("schedule", Json.Default)
        val iconData: Column<String?> = text("icon_data").nullable()
        val iconType: Column<String?> = text("icon_type").nullable()
        val deleted: Column<Boolean> = bool("deleted").default(false)

        override val primaryKey = PrimaryKey(instanceId)
    }

    object DeviceStates : Table() {
        val order: Column<Int> = integer("order")
        val instanceId: Column<InstanceId> = uuid("instance_id")
        val deviceState: Column<DeviceState> = text("device_state")
        val title: Column<String> = text("title")
        val icon: Column<String?> = text("icon").nullable()
        val description: Column<String?> = text("description").nullable()

        override val primaryKey = PrimaryKey(instanceId, deviceState)
    }

    object ScreenTimes : Table() {
        val app: Column<String> = text("app")
        val instanceId: Column<InstanceId> = uuid("instance_id") references Instances.instanceId
        val day: Column<LocalDate> = date("day")
        val screenTimeSeconds: Column<Long> = long("screen_time_seconds")
        val updatedAt: Column<Instant> = timestamp("updated_at")

        override val primaryKey = PrimaryKey(instanceId, day, app)
    }

    fun findUser(username: String): UserDto? = Users.selectAll()
        .where { Users.username eq username }
        .firstOrNull()
        ?.let {
            UserDto(
                username = it[Users.username],
                passwordSha256 = it[Users.passwordSha256]
            )
        }


    @Throws(InvalidPassword::class)
    fun validatePassword(username: String, password: String) {
        val count = Users.select(Users.username)
            .where { (Users.username eq username) and (Users.passwordSha256 eq password.sha256()) }
            .count()
        if (count == 0L)
            throw InvalidPassword()
    }

    fun validateInstanceToken(instanceId: InstanceId, instanceToken: String): InstanceId? {
        val rows = Instances.select(Instances.instanceId)
            .where { (Instances.instanceId eq instanceId) and (Instances.instanceTokenSha256 eq instanceToken.sha256()) and (Instances.deleted eq false) }
        return rows.firstOrNull()
            ?.get(Instances.instanceId)
    }

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    fun setupNewInstance(username: String, instanceName: String, clientType: String): NewInstanceDto {
        if (instanceName.length < 3)
            throw IllegalInstanceName(instanceName)
        val count = Instances.select(Instances.instanceId)
            .where { (Instances.username eq username) and (Instances.instanceName eq instanceName) and (Instances.deleted eq false) }
            .count()
        if (count > 0)
            throw InstanceAlreadyExists(instanceName)
        var instanceId: UUID
        do {
            instanceId = UUID.randomUUID()
        } while (Instances.selectAll().where { Instances.instanceId eq instanceId }.count() > 0)
        val instanceToken = UUID.randomUUID().toString()
        Instances.insert {
            it[Instances.username] = username
            it[Instances.instanceId] = instanceId
            it[Instances.instanceName] = instanceName
            it[Instances.instanceTokenSha256] = instanceToken.sha256()
            it[Instances.clientType] = clientType
            it[Instances.clientVersion] = "v0"
            it[Instances.schedule] = schedulePacker.pack(WeeklyScheduleDto.empty())
            it[Instances.clientTimezoneOffsetSeconds] = 0
        }
        return NewInstanceDto(instanceId, instanceToken)
    }

    fun saveReport(
        instanceId: InstanceId,
        day: LocalDate,
        screenTimeSeconds: Long,
        applicationsSeconds: Map<String, Long>
    ) {
        val total = applicationsSeconds + mapOf(TOTAL_TIME to screenTimeSeconds)
        ScreenTimes.batchUpsert(total.entries) { (app, seconds) ->
            this[ScreenTimes.instanceId] = instanceId
            this[ScreenTimes.day] = day
            this[ScreenTimes.screenTimeSeconds] = seconds
            this[ScreenTimes.app] = app
            this[ScreenTimes.updatedAt] = Clock.System.now()
        }
    }

    fun getInstances(username: String) = Instances
        .selectAll()
        .where { (Instances.username eq username) and (Instances.deleted eq false) }
        .orderBy(Instances.instanceName)
        .map {
            InstanceDto(
                id = it[Instances.instanceId],
                name = it[Instances.instanceName],
                forcedDeviceState = it[Instances.forcedDeviceState],
                clientVersion = it[Instances.clientVersion],
                clientType = it[Instances.clientType],
                schedule = schedulePacker.unpack(it[Instances.schedule]),
                iconData = it[Instances.iconData],
                iconType = it[Instances.iconType],
                clientTimezoneOffsetSeconds = it[Instances.clientTimezoneOffsetSeconds]
            )
        }

    fun getInstance(instanceId: InstanceId) = Instances
        .selectAll()
        .where { (Instances.instanceId eq instanceId) and (Instances.deleted eq false) }
        .map {
            InstanceDto(
                id = it[Instances.instanceId],
                name = it[Instances.instanceName],
                forcedDeviceState = it[Instances.forcedDeviceState],
                clientVersion = it[Instances.clientVersion],
                clientType = it[Instances.clientType],
                schedule = schedulePacker.unpack(it[Instances.schedule]),
                iconData = it[Instances.iconData],
                iconType = it[Instances.iconType],
                clientTimezoneOffsetSeconds = it[Instances.clientTimezoneOffsetSeconds]
            )
        }
        .firstOrNull()

    fun updateInstance(instanceId: InstanceId, update: UpdateInstanceDto) {
        Instances.update({ Instances.instanceId eq instanceId }) {
            it[Instances.instanceName] = update.name
            it[Instances.iconData] = update.iconData
            it[Instances.iconType] = update.iconType
        }
    }

    fun getScreenTimes(id: InstanceId, day: LocalDate): Map<String, ScreenTimeDto> = ScreenTimes
        .select(ScreenTimes.app, ScreenTimes.screenTimeSeconds, ScreenTimes.updatedAt)
        .where { (ScreenTimes.instanceId eq id) and (ScreenTimes.day eq day) }
        .associate {
            it[ScreenTimes.app] to ScreenTimeDto(
                it[ScreenTimes.screenTimeSeconds],
                it[ScreenTimes.updatedAt]
            )
        }

    fun setInstanceSchedule(id: InstanceId, schedule: WeeklyScheduleDto) {
        Instances.update({ Instances.instanceId eq id }) {
            it[Instances.schedule] = schedulePacker.pack(schedule)
        }
    }

    fun setForcedInstanceState(id: InstanceId, state: DeviceState?) {
        Instances.update({ Instances.instanceId eq id }) {
            it[forcedDeviceState] = state
        }
    }

    fun updateClientInformation(id: InstanceId, version: String, timezoneOffsetSeconds: Int) = Instances
        .update({ Instances.instanceId eq id }) {
            it[Instances.clientVersion] = version
            it[Instances.clientTimezoneOffsetSeconds] = timezoneOffsetSeconds
        }

    fun deleteInstance(instanceId: InstanceId) = Instances
        .update({ Instances.instanceId eq instanceId }) {
            it[deleted] = true
        }


    fun getAvailableDeviceStates(id: InstanceId) = DeviceStates
        .select(DeviceStates.deviceState, DeviceStates.title, DeviceStates.icon, DeviceStates.description)
        .where { DeviceStates.instanceId eq id }
        .orderBy(DeviceStates.order)
        .map {
            DescriptiveDeviceStateDto(
                deviceState = it[DeviceStates.deviceState],
                title = it[DeviceStates.title],
                icon = it[DeviceStates.icon],
                description = it[DeviceStates.description]
            )
        }
        .ensureActiveIsPresent()

    fun List<DescriptiveDeviceStateDto>.ensureActiveIsPresent(): List<DescriptiveDeviceStateDto> {
        return if (this.find { it.deviceState == DEFAULT_STATE } == null) {
            this + DescriptiveDeviceStateDto(
                deviceState = DEFAULT_STATE,
                title = "Active",
                icon = null,
                description = null
            )
        } else {
            this
        }
    }

    fun updateAvailableDeviceStates(id: InstanceId, states: List<DescriptiveDeviceStateDto>) {
        states.forEachIndexed { index, state ->
            DeviceStates.upsert {
                it[DeviceStates.instanceId] = id
                it[DeviceStates.deviceState] = state.deviceState
                it[DeviceStates.icon] = state.icon
                it[DeviceStates.title] = state.title
                it[DeviceStates.description] = state.description
                it[DeviceStates.order] = index
            }
            DeviceStates.deleteWhere {
                (DeviceStates.instanceId eq id) and (DeviceStates.deviceState notInList states.map { it.deviceState })
            }
        }
    }

    companion object {
        const val TOTAL_TIME = "## screentime ##"
    }


}

typealias InstanceId = UUID

class InvalidPassword : RuntimeException("Invalid password")
class IllegalInstanceName(val instanceName: String) : RuntimeException("Instance $instanceName already exists")
class InstanceAlreadyExists(val instanceName: String) :
    RuntimeException("Instance $instanceName has incorrect name")

data class UserDto(
    val username: String,
    val passwordSha256: String
)

data class NewInstanceDto(
    val instanceId: InstanceId,
    val token: String
)

data class ScreenTimeDto(
    val screenTimeSeconds: Long,
    val updatedAt: Instant
)

data class InstanceDto(
    val id: InstanceId,
    val name: String,
    val forcedDeviceState: DeviceState?,
    val clientType: String,
    val clientVersion: String,
    val schedule: WeeklyScheduleDto,
    val clientTimezoneOffsetSeconds: Int,
    val iconData: String? = null,
    val iconType: String? = null,
)

data class UpdateInstanceDto(
    val instanceId: InstanceId,
    val name: String,
    val iconData: String?,
    val iconType: String?
)

typealias DeviceState = String

const val DEFAULT_STATE: DeviceState = "ACTIVE"

data class DescriptiveDeviceStateDto(
    val deviceState: DeviceState,
    val title: String,
    val icon: String?,
    val description: String?
)

@Serializable
data class WeeklyScheduleDto(
    val schedule: Map<DayOfWeek, DailyScheduleDto>
) {
    companion object {
        fun empty() = WeeklyScheduleDto(DayOfWeek.entries.associateWith { DailyScheduleDto(emptyList()) })
    }
}

@Serializable
data class DailyScheduleDto(
    val periods: List<PeriodDto>
)

@Serializable
data class PeriodDto(
    val fromSeconds: Long,
    val toSeconds: Long,
    val deviceState: DeviceState
)
package pl.zarajczyk.familyrules.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class DbConnector {

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
        val forcedDeviceState: Column<DeviceState?> = text("forced_device_state").nullable() //(text("forced_device_state") references DeviceStates.deviceState).nullable()
        val clientVersion: Column<String> = text("client_version")

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

//    object Periods : Table() {
//        val id: Column<Long> = long("id").autoIncrement()
//        val instanceId: Column<InstanceId> = uuid("instance_id")
//        val day: Column<Day> = enumeration("day", Day::class)
//        val fromSeconds: Column<Long> = long("from_seconds")
//        val toSeconds: Column<Long> = long("to_seconds")
//        val deviceState: Column<DeviceState> = text("device_state_str")
//        val deviceStateCountdown: Column<Int> = integer("device_state_countdown")
//    }

    @Throws(InvalidPassword::class)
    fun validatePassword(username: String, password: String) {
        val count = Users.select(Users.username)
            .where { (Users.username eq username) and (Users.passwordSha256 eq password.sha256()) }
            .count()
        if (count == 0L)
            throw InvalidPassword()
    }

    @Throws(InvalidPassword::class)
    fun validatePasswordAndCreateOneTimeToken(username: String, password: String, seed: String): String {
        val user = Users.select(Users.passwordSha256)
            .where { (Users.username eq username) and (Users.passwordSha256 eq password.sha256()) }
        if (user.count() == 0L)
            throw InvalidPassword()
        return createOneTimeToken(user.first()[Users.passwordSha256], seed)
    }

    private fun createOneTimeToken(passwordSha256: String, seed: String) = "${seed}/${passwordSha256}".sha256()

    @Throws(InvalidPassword::class)
    fun validateOneTimeToken(username: String, token: String, seed: String) {
        val user = Users
            .select(Users.passwordSha256)
            .where { Users.username eq username }
        if (user.count() == 0L)
            throw InvalidPassword()
        val expectedToken = createOneTimeToken(user.first()[Users.passwordSha256], seed)
        if (expectedToken != token)
            throw InvalidPassword()
    }

    @Throws(InvalidPassword::class)
    fun validateInstanceToken(username: String, instanceName: String, instanceToken: String): InstanceId {
        val rows = Instances.select(Instances.instanceId)
            .where { (Instances.username eq username) and (Instances.instanceName eq instanceName) and (Instances.instanceTokenSha256 eq instanceToken.sha256()) }
        if (rows.count() == 0L)
            throw InvalidPassword()
        return rows.first()[Instances.instanceId]
    }

    @Throws(InvalidPassword::class)
    fun validateInstanceToken(username: String, instanceId: InstanceId, instanceToken: String): InstanceId {
        val rows = Instances.select(Instances.instanceId)
            .where { (Instances.username eq username) and (Instances.instanceId eq instanceId) and (Instances.instanceTokenSha256 eq instanceToken.sha256()) }
        if (rows.count() == 0L)
            throw InvalidPassword()
        return rows.first()[Instances.instanceId]
    }

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    fun setupNewInstance(username: String, instanceName: String, clientType: String): NewInstanceDto {
        if (instanceName.length < 3)
            throw IllegalInstanceName(instanceName)
        val count = Instances.select(Instances.instanceId)
            .where { (Instances.username eq username) and (Instances.instanceName eq instanceName) }
            .count()
        if (count > 0)
            throw InstanceAlreadyExists(instanceName)
        val instanceId = UUID.randomUUID()
        val instanceToken = UUID.randomUUID().toString()
        Instances.insert {
            it[Instances.username] = username
            it[Instances.instanceId] = instanceId
            it[Instances.instanceName] = instanceName
            it[Instances.instanceTokenSha256] = instanceToken.sha256()
            it[Instances.clientType] = clientType
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
        total.forEach { (app, seconds) ->
            ScreenTimes.upsert {
                it[ScreenTimes.instanceId] = instanceId
                it[ScreenTimes.day] = day
                it[ScreenTimes.screenTimeSeconds] = seconds
                it[ScreenTimes.app] = app
                it[ScreenTimes.updatedAt] = Clock.System.now()
            }
        }
    }

    fun getInstances(username: String) = Instances
        .select(Instances.instanceId, Instances.instanceName, Instances.forcedDeviceState, Instances.clientVersion, Instances.clientType)
        .where { Instances.username eq username }
        .orderBy(Instances.instanceName)
        .map {
            InstanceDto(
                id = it[Instances.instanceId],
                name = it[Instances.instanceName],
                forcedDeviceState = it[Instances.forcedDeviceState],
                clientVersion = it[Instances.clientVersion],
                clientType = it[Instances.clientType]
            )
        }

    fun getInstance(instanceId: InstanceId) = Instances
        .select(Instances.instanceId, Instances.instanceName, Instances.forcedDeviceState, Instances.clientVersion, Instances.clientType)
        .where { Instances.instanceId eq instanceId }
        .map {
            InstanceDto(
                id = it[Instances.instanceId],
                name = it[Instances.instanceName],
                forcedDeviceState = it[Instances.forcedDeviceState],
                clientVersion = it[Instances.clientVersion],
                clientType = it[Instances.clientType]
            )
        }
        .firstOrNull()

    fun getScreenTimes(id: InstanceId, day: LocalDate): Map<String, ScreenTimeDto> = ScreenTimes
        .select(ScreenTimes.app, ScreenTimes.screenTimeSeconds, ScreenTimes.updatedAt)
        .where { (ScreenTimes.instanceId eq id) and (ScreenTimes.day eq day) }
        .associate { it[ScreenTimes.app] to ScreenTimeDto(it[ScreenTimes.screenTimeSeconds], it[ScreenTimes.updatedAt]) }

    fun setForcedInstanceState(id: InstanceId, state: DeviceState?) {
        Instances.update({ Instances.instanceId eq id }) {
            it[forcedDeviceState] = state
        }
    }

    fun updateClientVersion(id: InstanceId, version: String) = Instances
        .update({ Instances.instanceId eq id }) { it[Instances.clientVersion] = version }

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

//    fun getInstanceSchedule(id: InstanceId): ScheduleDto =
//        Periods
//            .select(
//                Periods.day,
//                Periods.fromSeconds,
//                Periods.toSeconds,
//                Periods.deviceState,
//                Periods.deviceStateCountdown
//            )
//            .where { Periods.instanceId eq id }
//            .map {
//                it[Periods.day] to PeriodDto(
//                    it[Periods.fromSeconds],
//                    it[Periods.toSeconds],
//                    it[Periods.deviceState],
//                    it[Periods.deviceStateCountdown]
//                )
//            }
//            .groupBy { it.first }
//            .mapValues { (_, v) -> PeriodsDto(v.map { it.second }) }
//            .let { ScheduleDto(it) }
//
//    fun setInstanceSchedule(id: InstanceId, scheduleDto: ScheduleDto) {
//        Periods.deleteWhere { Periods.instanceId eq id }
//        scheduleDto.schedule.forEach { (day, periods) ->
//            periods.periods.forEach { period ->
//                Periods.insert {
//                    it[Periods.instanceId] = id
//                    it[Periods.day] = day
//                    it[Periods.fromSeconds] = period.fromSeconds
//                    it[Periods.toSeconds] = period.toSeconds
//                    it[Periods.deviceState] = period.deviceState
//                    it[Periods.deviceStateCountdown] = period.deviceStateCountdown
//                }
//            }
//        }
//
//
//    }
//

    companion object {
        const val TOTAL_TIME = "## screentime ##"
    }

}

typealias InstanceId = UUID

class InvalidPassword : RuntimeException("Invalid password")
class IllegalInstanceName(val instanceName: String) : RuntimeException("Instance $instanceName already exists")
class InstanceAlreadyExists(val instanceName: String) :
    RuntimeException("Instance $instanceName has incorrect name")

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
    val clientVersion: String
)

typealias DeviceState = String

data class DescriptiveDeviceStateDto(
    val deviceState: DeviceState,
    val title: String,
    val icon: String?,
    val description: String?
)
//data class ScheduleDto(
//    val schedule: Map<Day, PeriodsDto>
//)
//
//data class PeriodsDto(
//    val periods: List<PeriodDto>
//)
//
//data class PeriodDto(
//    val fromSeconds: Long,
//    val toSeconds: Long,
//    val deviceState: DeviceState,
//    val deviceStateCountdown: Int
//)
//
//enum class Day {
//    MON, TUE, WED, THU, FRI, SAT, SUN
//}
package pl.zarajczyk.familyrules.shared

import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.date
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
        val id: Column<InstanceId> = long("id").autoIncrement()
        val username: Column<String> = text("username")
        val instanceName: Column<String> = text("instance_name")
        val instanceTokenSha256: Column<String> = text("instance_token_sha256")
        val os: Column<SupportedOs> = enumeration("os", SupportedOs::class)

        override val primaryKey = PrimaryKey(id)
    }

    object ScreenTimes : Table() {
        val id: Column<Long> = long("id").autoIncrement()
        val app: Column<String> = text("app")
        val instanceId: Column<InstanceId> = long("instance_id")
        val day: Column<LocalDate> = date("day")
        val screenTimeSeconds: Column<Long> = long("screen_time_seconds")

        override val primaryKey = PrimaryKey(Instances.id)
    }

    object States : Table() {
        val id: Column<Long> = long("id").autoIncrement()
        val instanceId: Column<InstanceId> = long("instance_id")
        val deviceState: Column<DeviceState> = enumeration("device_state", DeviceState::class)
        val deviceStateCountdown: Column<Int> = integer("device_state_countdown")
    }

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
        val rows = Instances.select(Instances.id)
            .where { (Instances.username eq username) and (Instances.instanceName eq instanceName) and (Instances.instanceTokenSha256 eq instanceToken.sha256()) }
        if (rows.count() == 0L)
            throw InvalidPassword()
        return rows.first()[Instances.id]
    }

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    fun setupNewInstance(username: String, instanceName: String, os: SupportedOs): String {
        if (instanceName.length < 3)
            throw IllegalInstanceName(instanceName)
        val count = Instances.select(Instances.id)
            .where { (Instances.username eq username) and (Instances.instanceName eq instanceName) }
            .count()
        if (count > 0)
            throw InstanceAlreadyExists(instanceName)
        val instanceToken = UUID.randomUUID().toString()
        Instances.insert {
            it[Instances.username] = username
            it[Instances.instanceName] = instanceName
            it[Instances.instanceTokenSha256] = instanceToken.sha256()
            it[Instances.os] = os
        }
        return instanceToken
    }

    fun saveReport(
        instanceId: InstanceId,
        day: LocalDate,
        screenTimeSeconds: Long,
        applicationsSeconds: Map<String, Long>
    ) {
        val total = applicationsSeconds + mapOf(TOTAL_TIME to screenTimeSeconds)
        total.forEach { (app, seconds) ->
            val existingEntryId = ScreenTimes.select(ScreenTimes.id)
                .where { (ScreenTimes.instanceId eq instanceId) and (ScreenTimes.day eq day) and (ScreenTimes.app eq app) }
                .firstOrNull()
                ?.get(ScreenTimes.id)
            if (existingEntryId == null) {
                ScreenTimes.insert {
                    it[ScreenTimes.instanceId] = instanceId
                    it[ScreenTimes.day] = day
                    it[ScreenTimes.screenTimeSeconds] = seconds
                    it[ScreenTimes.app] = app
                }
            } else {
                ScreenTimes.update({ (ScreenTimes.id eq existingEntryId) and (ScreenTimes.app eq app) }) {
                    it[ScreenTimes.screenTimeSeconds] = seconds
                }
            }
        }
    }

    fun getInstances(username: String) = Instances
        .select(Instances.id, Instances.instanceName)
        .where { Instances.username eq username }
        .map { InstanceDto(id = it[Instances.id], name = it[Instances.instanceName]) }

    fun getScreenTimeSeconds(id: InstanceId, day: LocalDate): Map<String, Long> = ScreenTimes
        .select(ScreenTimes.app, ScreenTimes.screenTimeSeconds)
        .where { (ScreenTimes.instanceId eq id) and (ScreenTimes.day eq day) }
        .associate { it[ScreenTimes.app] to it[ScreenTimes.screenTimeSeconds] }

    fun setInstanceState(id: InstanceId, state: StateDto) {
        val existingId = States.select(States.id).where { States.instanceId eq id }.map { it[States.id] }.firstOrNull()
        if (existingId == null) {
            States.insert {
                it[States.instanceId] = id
                it[States.deviceState] = state.deviceState
                it[States.deviceStateCountdown] = state.deviceStateCountdown
            }
        } else {
            States.update({ States.id eq existingId }) {
                it[States.deviceState] = state.deviceState
                it[States.deviceStateCountdown] = state.deviceStateCountdown
            }
        }
    }

    fun getInstanceState(id: InstanceId) =
        States
            .select(States.deviceState, States.deviceStateCountdown)
            .where { States.instanceId eq id }
            .map { StateDto(it[States.deviceState], it[States.deviceStateCountdown]) }
            .firstOrNull()

    companion object {
        const val TOTAL_TIME = "## screentime ##"
    }

}

typealias InstanceId = Long

class InvalidPassword : RuntimeException("Invalid password")
class IllegalInstanceName(val instanceName: String) : RuntimeException("Instance $instanceName already exists")
class InstanceAlreadyExists(val instanceName: String) :
    RuntimeException("Instance $instanceName has incorrect name")

data class InstanceDto(
    val id: InstanceId,
    val name: String
)

data class StateDto(
    val deviceState: DeviceState,
    val deviceStateCountdown: Int
)

enum class DeviceState {
    ACTIVE, LOCKED, LOGGED_OUT, APP_DISABLED
}
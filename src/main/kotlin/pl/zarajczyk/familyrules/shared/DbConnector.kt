package pl.zarajczyk.familyrules.shared

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.*
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
        val lockedSince: Column<Instant?> = timestamp("locked_since").nullable()
        val loggedOutSince: Column<Instant?> = timestamp("logged_out_since").nullable()
    }

    fun getPasswordHash(username: String): String? {
        val users = Users.select(Users.passwordSha256)
            .where { Users.username eq username }
        return users.firstOrNull()?.get(Users.passwordSha256)
    }

    fun getInstance(username: String, instanceName: String): InstanceDtoWithHash? {
        val rows = Instances.select(Instances.id, Instances.instanceTokenSha256)
            .where { (Instances.username eq username) and (Instances.instanceName eq instanceName) }
        return rows.firstOrNull()
            ?.let { InstanceDtoWithHash(it[Instances.id], instanceName, it[Instances.instanceTokenSha256]) }
    }

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    fun setupNewInstance(username: String, instanceName: String, instanceTokenHash: String, os: SupportedOs) {
        if (instanceName.length < 3)
            throw IllegalInstanceName(instanceName)
        val count = Instances.select(Instances.id)
            .where { (Instances.username eq username) and (Instances.instanceName eq instanceName) }
            .count()
        if (count > 0)
            throw InstanceAlreadyExists(instanceName)
        Instances.insert {
            it[Instances.username] = username
            it[Instances.instanceName] = instanceName
            it[Instances.instanceTokenSha256] = instanceTokenHash
            it[Instances.os] = os
        }
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
                it[States.lockedSince] = state.lockedSince
                it[States.loggedOutSince] = state.loggedOutSince
            }
        } else {
            States.update({ States.id eq existingId }) {
                it[States.lockedSince] = state.lockedSince
                it[States.loggedOutSince] = state.loggedOutSince
            }
        }
    }

    fun getInstanceState(id: InstanceId) =
        States
            .select(States.lockedSince, States.loggedOutSince)
            .where { States.instanceId eq id }
            .map { StateDto(it[States.lockedSince], it[States.loggedOutSince]) }
            .firstOrNull()

    companion object {
        const val TOTAL_TIME = "## screentime ##"
    }

}

typealias InstanceId = Long
typealias UserName = String

class IllegalInstanceName(val instanceName: String) : RuntimeException("Instance $instanceName already exists")
class InstanceAlreadyExists(val instanceName: String) :
    RuntimeException("Instance $instanceName has incorrect name")

data class InstanceDto(
    val id: InstanceId,
    val name: String
)

data class InstanceDtoWithHash(
    val id: InstanceId,
    val name: String,
    val tokenHash: String
)

data class StateDto(
    val lockedSince: Instant?,
    val loggedOutSince: Instant?
) {
    companion object {
        fun empty() = StateDto(null, null)
    }
}
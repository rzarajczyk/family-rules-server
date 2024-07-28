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

        override val primaryKey = PrimaryKey(id)
    }

    object ScreenTimes : Table() {
        val id: Column<Long> = long("id").autoIncrement()
        val instanceId: Column<InstanceId> = long("instance_id")
        val day: Column<LocalDate> = date("day")
        val screenTimeSeconds: Column<Long> = long("screen_time_seconds")

        override val primaryKey = PrimaryKey(Instances.id)
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
    fun validateInstanceToken(username: String, instanceName: String, instanceToken: String): InstanceId {
        val rows = Instances.select(Instances.id)
            .where { (Instances.username eq username) and (Instances.instanceName eq instanceName) and (Instances.instanceTokenSha256 eq instanceToken.sha256()) }
        if (rows.count() == 0L)
            throw InvalidPassword()
        return rows.first()[Instances.id]
    }

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    fun setupNewInstance(username: String, instanceName: String): String {
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
        }
        return instanceToken
    }

    fun saveReport(instanceId: InstanceId, day: LocalDate, screenTimeSeconds: Long, applicationsSeconds: Map<String, Long>) {
        val existingEntryId = ScreenTimes.select(ScreenTimes.id)
            .where { (ScreenTimes.instanceId eq instanceId) and (ScreenTimes.day eq day)  }
            .firstOrNull()
            ?.get(ScreenTimes.id)
        if (existingEntryId == null) {
            ScreenTimes.insert {
                it[ScreenTimes.instanceId] = instanceId
                it[ScreenTimes.day] = day
                it[ScreenTimes.screenTimeSeconds] = screenTimeSeconds
            }
        } else {
            ScreenTimes.update({ ScreenTimes.id eq existingEntryId }) {
                it[ScreenTimes.screenTimeSeconds] = screenTimeSeconds
            }
        }
    }

}

typealias InstanceId = Long

class InvalidPassword : RuntimeException("Invalid password")
class IllegalInstanceName(val instanceName: String) : RuntimeException("Instance $instanceName already exists")
class InstanceAlreadyExists(val instanceName: String) :
    RuntimeException("Instance $instanceName has incorrect name")
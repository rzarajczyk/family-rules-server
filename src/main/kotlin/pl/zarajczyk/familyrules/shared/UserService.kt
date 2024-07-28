package pl.zarajczyk.familyrules.shared

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class UserService {

    object Users : Table() {
        val username: Column<String> = text("username")
        val passwordSha256: Column<String> = text("password_sha256")

        override val primaryKey = PrimaryKey(username)
    }

    object Instances : Table() {
        val id: Column<Long> = long("id").autoIncrement()
        val username: Column<String> = text("username")
        val instanceName: Column<String> = text("instance_name")
        val instanceTokenSha256: Column<String> = text("instance_token_sha256")
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
    fun validateInstanceToken(username: String, instanceName: String, instanceToken: String) {
        val count = Instances.select(Instances.username)
            .where { (Instances.username eq username) and (Instances.instanceName eq instanceName) and (Instances.instanceTokenSha256 eq instanceToken.sha256()) }
            .count()
        if (count == 0L)
            throw InvalidPassword()
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

}

class InvalidPassword : RuntimeException("Invalid password")
class IllegalInstanceName(val instanceName: String) : RuntimeException("Instance $instanceName already exists")
class InstanceAlreadyExists(val instanceName: String) :
    RuntimeException("Instance $instanceName has incorrect name")
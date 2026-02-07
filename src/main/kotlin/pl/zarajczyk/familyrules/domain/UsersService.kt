package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.UserRef
import pl.zarajczyk.familyrules.domain.port.UsersRepository

@Service
class UsersService(private val usersRepository: UsersRepository) {
    fun get(username: String): User {
        val ref = usersRepository.get(username) ?: throw UserNotFoundException(username)
        return RefBasedUser(ref, usersRepository)
    }

    @Deprecated("avoid refs on a Service level")
    fun get(ref: UserRef): User {
        return RefBasedUser(ref, usersRepository)
    }

    fun getAllUsers() = usersRepository.getAll()
        .map { RefBasedUser(it, usersRepository) }

    fun userExists(username: String) = usersRepository.get(username) != null

    fun createUser(username: String, password: String, accessLevel: AccessLevel): User =
        usersRepository
            .createUser(username, password.sha256(), accessLevel)
            .let { RefBasedUser(it, usersRepository) }


}

interface User {
    fun asRef(): UserRef

    fun fetchDetails(): UserDetails

    fun delete()

    @Throws(InvalidPassword::class)
    fun validatePassword(password: String)

    fun changePassword(newPassword: String)
    
    fun updateWebhookSettings(webhookEnabled: Boolean, webhookUrl: String?)
    
    fun updateLastActivity(lastActivityMillis: Long)
}

data class RefBasedUser(
    val userRef: UserRef,
    private val usersRepository: UsersRepository
) : User {
    override fun asRef(): UserRef = userRef

    override fun fetchDetails(): UserDetails {
        return usersRepository.fetchDetails(userRef).let {
            UserDetails(
                username = it.username,
                passwordSha256 = it.passwordSha256,
                accessLevel = it.accessLevel,
                webhookEnabled = it.webhookEnabled,
                webhookUrl = it.webhookUrl
            )
        }
    }

    override fun delete() {
        usersRepository.delete(userRef)
    }

    override fun validatePassword(password: String) {
        val user = usersRepository.fetchDetails(userRef, includePasswordHash = true)
        if (user.passwordSha256 != password.sha256())
            throw InvalidPassword()
    }

    override fun changePassword(newPassword: String) {
        usersRepository.update(userRef, newPassword.sha256())
    }
    
    override fun updateWebhookSettings(webhookEnabled: Boolean, webhookUrl: String?) {
        usersRepository.updateWebhookSettings(userRef, webhookEnabled, webhookUrl)
    }
    
    override fun updateLastActivity(lastActivityMillis: Long) {
        usersRepository.updateLastActivity(userRef, lastActivityMillis)
    }
}

class InvalidPassword : RuntimeException("Invalid password")

data class UserDetails(
    val username: String,
    val passwordSha256: String,
    val accessLevel: AccessLevel = AccessLevel.ADMIN,
    val webhookEnabled: Boolean = false,
    val webhookUrl: String? = null
)
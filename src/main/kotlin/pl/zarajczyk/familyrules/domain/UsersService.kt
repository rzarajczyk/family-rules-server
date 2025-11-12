package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.UserRef
import pl.zarajczyk.familyrules.domain.port.UsersRepository

@Service
class UsersService(private val usersRepository: UsersRepository) {

    fun <T> withUserContext(username: String, action: (user: User) -> T): T {
        val ref = usersRepository.get(username) ?: throw UserNotFoundException(username)
        val user = RefBasedUser(ref, usersRepository)
        return action(user)
    }

    @Deprecated("avoid refs on a Service level")
    fun <T> withUserContext(ref: UserRef, action: (user: User) -> T): T {
        val user = RefBasedUser(ref, usersRepository)
        return action(user)
    }

    fun listAllUsers() = usersRepository.getAll()
        .map { RefBasedUser(it, usersRepository) }

    fun userExists(username: String) = usersRepository.get(username) != null

    fun createUser(username: String, password: String, accessLevel: AccessLevel): User =
        usersRepository
            .createUser(username, password.sha256(), accessLevel)
            .let { RefBasedUser(it, usersRepository) }


}

interface User {
    fun asRef(): UserRef

    fun get(): UserDetails

    fun delete()

    @Throws(InvalidPassword::class)
    fun validatePassword(password: String)

    fun changePassword(newPassword: String)
}

class RefBasedUser(
    val userRef: UserRef,
    private val usersRepository: UsersRepository
) : User {
    override fun asRef(): UserRef = userRef

    override fun get(): UserDetails {
        return usersRepository.fetchDetails(userRef).let {
            UserDetails(it.username, it.passwordSha256, it.accessLevel)
        }
    }

    override fun delete() {
        usersRepository.delete(userRef)
    }

    override fun validatePassword(password: String) {
        val user = usersRepository.fetchDetails(userRef)
        if (user.passwordSha256 != password.sha256())
            throw InvalidPassword()
    }

    override fun changePassword(newPassword: String) {
        usersRepository.update(userRef, newPassword.sha256())
    }
}

class InvalidPassword : RuntimeException("Invalid password")

data class UserDetails(
    val username: String,
    val passwordSha256: String,
    val accessLevel: AccessLevel = AccessLevel.ADMIN
)
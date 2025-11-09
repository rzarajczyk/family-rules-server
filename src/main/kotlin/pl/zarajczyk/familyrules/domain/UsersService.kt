package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service

@Service
class UsersService(private val usersRepository: UsersRepository) {

    fun <T> withUserContext(username: String, action: (user: User) -> T): T {
        val ref = usersRepository.get(username) ?: throw UserNotFoundException(username)
        val user = RefBasedUser(ref, usersRepository)
        return action(user)
    }

    fun listAllUsers() = usersRepository.getAll()
        .map { RefBasedUser(it, usersRepository) }

    fun userExists(username: String) = usersRepository.get(username) != null

    fun createUser(username: String, password: String, accessLevel: AccessLevel) =
        usersRepository.createUser(username, password.sha256(), accessLevel)

}

class UserNotFoundException(username: String) : RuntimeException("User $username not found")
class InvalidPassword : RuntimeException("Invalid password")

interface User {
    fun get(): UserDto

    fun delete()

    @Throws(InvalidPassword::class)
    fun validatePassword(password: String)

    fun changePassword(newPassword: String)
}

class RefBasedUser(
    val userRef: UserRef,
    val usersRepository: UsersRepository
) : User {
    override fun get(): UserDto {
        return usersRepository.fetchDetails(userRef)
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
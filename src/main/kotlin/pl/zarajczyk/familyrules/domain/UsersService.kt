package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service

@Service
class UsersService(private val usersRepository: UsersRepository) {

    fun <T> withUserContext(username: String, action: (user: User) -> T): T {
        val userDto = usersRepository.findUser(username) ?: throw UserNotFoundException(username)
        val user = DtoBasedUser(userDto, usersRepository)
        return action(user)
    }

    fun listAllUsers() = usersRepository.getAllUsers()
        .map { DtoBasedUser(it, usersRepository) }

    fun userExists(username: String) = usersRepository.findUser(username) != null

    fun createUser(username: String, password: String, accessLevel: AccessLevel) =
        usersRepository.createUser(username, password, accessLevel)

}

class UserNotFoundException(username: String) : RuntimeException("User $username not found")

interface User {
    val username: String
    val accessLevel: AccessLevel

    fun delete()
    fun validatePassword(password: String)
    fun changePassword(newPassword: String)
}

class DtoBasedUser(
    val userDto: UserDto,
    val usersRepository: UsersRepository
) : User {
    override val username: String
        get() = userDto.username
    override val accessLevel: AccessLevel
        get() = userDto.accessLevel

    override fun delete() {
        usersRepository.deleteUser(userDto.username)
    }

    override fun validatePassword(password: String) {
        usersRepository.validatePassword(userDto.username, password)
    }

    override fun changePassword(newPassword: String) {
        usersRepository.changePassword(userDto.username, newPassword)
    }
}
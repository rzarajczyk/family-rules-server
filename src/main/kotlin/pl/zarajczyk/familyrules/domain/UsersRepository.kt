package pl.zarajczyk.familyrules.domain

interface UsersRepository {
    // User operations
    fun findUser(username: String): UserDto?
    fun validatePassword(username: String, password: String)
    fun changePassword(username: String, newPassword: String)
    fun getAllUsers(): List<UserDto>
    fun deleteUser(username: String)
    fun createUser(username: String, password: String, accessLevel: AccessLevel)
}
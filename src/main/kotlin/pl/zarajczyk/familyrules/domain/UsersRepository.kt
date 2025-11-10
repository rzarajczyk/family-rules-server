package pl.zarajczyk.familyrules.domain

interface UsersRepository {
    fun createUser(username: String, passwordHash: String, accessLevel: AccessLevel): UserRef
    fun get(username: String): UserRef?
    fun getAll(): List<UserRef>
    fun fetchDetails(user: UserRef): UserDto
    fun update(user: UserRef, newPasswordHash: String)
    fun delete(user: UserRef)
}

/**
 * Represents abstract reference to the database object related to the given user
 */
interface UserRef
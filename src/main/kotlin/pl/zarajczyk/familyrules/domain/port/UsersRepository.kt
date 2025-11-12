package pl.zarajczyk.familyrules.domain.port

import pl.zarajczyk.familyrules.domain.AccessLevel

interface UsersRepository {
    fun createUser(username: String, passwordHash: String, accessLevel: AccessLevel): UserRef
    fun get(username: String): UserRef?
    fun getAll(): List<UserRef>
    fun fetchDetails(user: UserRef, includePasswordHash: Boolean = false): UserDetailsDto
    fun update(user: UserRef, newPasswordHash: String)
    fun delete(user: UserRef)
}

/**
 * Represents abstract reference to the database object related to the given user
 */
interface UserRef

data class UserDetailsDto(
    val username: String,
    val passwordSha256: String,
    val accessLevel: AccessLevel = AccessLevel.ADMIN
)
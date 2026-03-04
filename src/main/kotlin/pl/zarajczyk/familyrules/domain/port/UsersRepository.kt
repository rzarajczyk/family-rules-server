package pl.zarajczyk.familyrules.domain.port

import kotlinx.datetime.Instant
import pl.zarajczyk.familyrules.domain.AccessLevel

interface UsersRepository {
    fun createUser(username: String, passwordHash: String, accessLevel: AccessLevel): UserRef
    fun get(username: String): UserRef?
    fun getAll(): List<UserRef>
    fun update(user: UserRef, newPasswordHash: String)
    fun updateWebhookSettings(user: UserRef, webhookEnabled: Boolean, webhookUrl: String?)
    fun updateIntegrationApiToken(user: UserRef, token: String?)
    fun getByIntegrationApiToken(token: String): UserRef?
    fun updateLastActivity(user: UserRef, lastActivityMillis: Long)
    fun getUsersWithRecentActivity(since: Instant): List<UserRef>
    fun addWebhookCallHistory(user: UserRef, call: WebhookCallHistoryEntry)
    fun getWebhookCallHistory(user: UserRef): List<WebhookCallHistoryEntry>
    fun updateWebhookHistoryUntil(user: UserRef, until: Long?)
    fun deleteWebhookCallHistory(user: UserRef)
    fun delete(user: UserRef)
}

/**
 * Represents abstract reference to the database object related to the given user
 */
interface UserRef {
    val details: UserDetailsDto
    val passwordSha256: String
}

data class UserDetailsDto(
    val username: String,
    val accessLevel: AccessLevel = AccessLevel.ADMIN,
    val webhookEnabled: Boolean = false,
    val webhookUrl: String? = null,
    val integrationApiToken: String? = null,
    val lastActivity: Long? = null,
    val webhookHistoryUntil: Long? = null
)

data class WebhookCallHistoryEntry(
    val timestamp: Long,
    val status: String,
    val statusCode: Int?,
    val errorMessage: String?,
    val payload: String?
)
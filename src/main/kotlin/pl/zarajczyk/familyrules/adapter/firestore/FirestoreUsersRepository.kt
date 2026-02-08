package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.Timestamp
import kotlinx.datetime.Instant
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.AccessLevel
import pl.zarajczyk.familyrules.domain.port.UserDetailsDto
import pl.zarajczyk.familyrules.domain.port.UserRef
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import pl.zarajczyk.familyrules.domain.port.WebhookCallHistoryEntry

@Service
class FirestoreUsersRepository(
    private val firestore: Firestore,
) : UsersRepository {
    override fun get(username: String): UserRef? {
        return firestore
            .collection("users")
            .document(username)
            .let {
                if (it.get().get().exists())
                    FirestoreUserRef(it)
                else
                    null
            }
    }

    override fun fetchDetails(user: UserRef, includePasswordHash: Boolean): UserDetailsDto {
        val doc = (user as FirestoreUserRef).doc.get().get()
        return UserDetailsDto(
            username = doc.getStringOrThrow("username"),
            passwordSha256 = when (includePasswordHash) {
                true -> doc.getStringOrThrow("passwordSha256")
                false -> ""
            },
            accessLevel = doc.getStringOrThrow("accessLevel").let { AccessLevel.valueOf(it) },
            webhookEnabled = doc.getBoolean("webhookEnabled") ?: false,
            webhookUrl = doc.getString("webhookUrl"),
            lastActivity = doc.getTimestamp("lastActivity")?.toDate()?.time
        )
    }

    override fun update(user: UserRef, newPasswordHash: String) {
        val userRef = (user as FirestoreUserRef).doc
        userRef.update("passwordSha256", newPasswordHash).get()
    }

    override fun updateWebhookSettings(user: UserRef, webhookEnabled: Boolean, webhookUrl: String?) {
        val userRef = (user as FirestoreUserRef).doc
        val updates = mutableMapOf<String, Any?>("webhookEnabled" to webhookEnabled)
        if (webhookUrl != null) {
            updates["webhookUrl"] = webhookUrl
        }
        userRef.update(updates).get()
    }

    override fun updateLastActivity(user: UserRef, lastActivityMillis: Long) {
        val userRef = (user as FirestoreUserRef).doc
        val timestamp = Timestamp.ofTimeMicroseconds(lastActivityMillis * 1000)
        userRef.update("lastActivity", timestamp).get()
    }

    override fun getAll(): List<UserRef> {
        val snapshots = firestore.collection("users").get().get()
        return snapshots.documents.map { FirestoreUserRef(it.reference) }
    }

    override fun getUsersWithRecentActivity(since: Instant): List<UserRef> {
        // REQUIRED FIRESTORE COMPOSITE INDEX:
        // Collection: users
        // Fields: webhookEnabled (Ascending), lastActivity (Descending)
        // This index is defined in firestore.indexes.json
        val timestamp = Timestamp.ofTimeMicroseconds(since.toEpochMilliseconds() * 1000)
        val snapshots = firestore.collection("users")
            .whereEqualTo("webhookEnabled", true)
            .whereGreaterThan("lastActivity", timestamp)
            .get()
            .get()
        return snapshots.documents.map { FirestoreUserRef(it.reference) }
    }

    override fun addWebhookCallHistory(user: UserRef, call: WebhookCallHistoryEntry) {
        val userRef = (user as FirestoreUserRef).doc
        val historyCollection = userRef.collection("webhookCallHistory")
        
        val callData = mapOf(
            "timestamp" to call.timestamp,
            "status" to call.status,
            "statusCode" to call.statusCode,
            "errorMessage" to call.errorMessage,
            "payload" to call.payload
        )
        
        historyCollection.add(callData).get()
        
        // Keep only the last 120 entries
        val allEntries = historyCollection.orderBy("timestamp", com.google.cloud.firestore.Query.Direction.DESCENDING).get().get()
        if (allEntries.size() > 120) {
            // Delete oldest entries
            allEntries.documents.drop(120).forEach { doc ->
                doc.reference.delete().get()
            }
        }
    }

    override fun getWebhookCallHistory(user: UserRef): List<WebhookCallHistoryEntry> {
        val userRef = (user as FirestoreUserRef).doc
        val historyCollection = userRef.collection("webhookCallHistory")
        val snapshots = historyCollection
            .orderBy("timestamp", com.google.cloud.firestore.Query.Direction.DESCENDING)
            .limit(120)
            .get()
            .get()
        
        return snapshots.documents.map { doc ->
            WebhookCallHistoryEntry(
                timestamp = doc.getLong("timestamp")!!,
                status = doc.getString("status")!!,
                statusCode = doc.getLong("statusCode")?.toInt(),
                errorMessage = doc.getString("errorMessage"),
                payload = doc.getString("payload")
            )
        }
    }

    override fun delete(user: UserRef) {
        (user as FirestoreUserRef).doc.delete().get()
    }

    override fun createUser(username: String, passwordHash: String, accessLevel: AccessLevel): UserRef {
        val userData = mapOf(
            "username" to username,
            "passwordSha256" to passwordHash,
            "accessLevel" to accessLevel.name
        )

        val doc = firestore.collection("users").document(username)
        doc.set(userData).get()
        return FirestoreUserRef(doc)
    }
}

data class FirestoreUserRef(
    val doc: DocumentReference
) : UserRef
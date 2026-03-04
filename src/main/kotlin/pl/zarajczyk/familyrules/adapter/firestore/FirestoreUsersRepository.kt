package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.Timestamp
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
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
            .let { fetch(it) }
    }

    companion object {
        fun fetch(reference: DocumentReference): UserRef? =
            fetch(reference.get().get(), reference)

        fun fetch(snapshot: DocumentSnapshot): UserRef? =
            fetch(snapshot, snapshot.reference)

        private fun fetch(snapshot: DocumentSnapshot, reference: DocumentReference): UserRef? {
            if (!snapshot.exists()) return null
            return FirestoreUserRef(
                doc = reference,
                details = UserDetailsDto(
                    username = snapshot.getStringOrThrow("username"),
                    accessLevel = snapshot.getStringOrThrow("accessLevel").let { AccessLevel.valueOf(it) },
                    webhookEnabled = snapshot.getBoolean("webhookEnabled") ?: false,
                    webhookUrl = snapshot.getString("webhookUrl"),
                    integrationApiToken = snapshot.getString("integrationApiToken"),
                    lastActivity = snapshot.getTimestamp("lastActivity")?.toDate()?.time,
                    webhookHistoryUntil = snapshot.getLong("webhookHistoryUntil")
                ),
                passwordSha256 = snapshot.getStringOrThrow("passwordSha256")
            )
        }
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
        return snapshots.documents.mapNotNull { fetch(it) }
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
        return snapshots.documents.mapNotNull { fetch(it) }
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
    }

    override fun getWebhookCallHistory(user: UserRef): List<WebhookCallHistoryEntry> {
        val userRef = (user as FirestoreUserRef).doc
        val historyCollection = userRef.collection("webhookCallHistory")
        val snapshots = historyCollection
            .orderBy("timestamp", com.google.cloud.firestore.Query.Direction.DESCENDING)
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

    override fun updateIntegrationApiToken(user: UserRef, token: String?) {
        val userRef = (user as FirestoreUserRef).doc
        if (token != null) {
            userRef.update("integrationApiToken", token).get()
        } else {
            userRef.update("integrationApiToken", com.google.cloud.firestore.FieldValue.delete()).get()
        }
    }

    override fun getByIntegrationApiToken(token: String): UserRef? {
        val snapshots = firestore.collection("users")
            .whereEqualTo("integrationApiToken", token)
            .limit(1)
            .get()
            .get()
        return snapshots.documents.firstOrNull()?.let { fetch(it) }
    }

    override fun updateWebhookHistoryUntil(user: UserRef, until: Long?) {
        val userRef = (user as FirestoreUserRef).doc
        if (until != null) {
            userRef.update("webhookHistoryUntil", until).get()
        } else {
            userRef.update("webhookHistoryUntil", com.google.cloud.firestore.FieldValue.delete()).get()
        }
    }

    override fun deleteWebhookCallHistory(user: UserRef) {
        val userRef = (user as FirestoreUserRef).doc
        val historyCollection = userRef.collection("webhookCallHistory")
        val batch = firestore.batch()
        historyCollection.get().get().documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().get()
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
        return fetch(doc) ?: throw RuntimeException("Created user with name $username not found")
    }
}

data class FirestoreUserRef(
    val doc: DocumentReference,
    override val details: UserDetailsDto,
    override val passwordSha256: String
) : UserRef
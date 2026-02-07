package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.AccessLevel
import pl.zarajczyk.familyrules.domain.port.UserDetailsDto
import pl.zarajczyk.familyrules.domain.port.UserRef
import pl.zarajczyk.familyrules.domain.port.UsersRepository

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
            webhookUrl = doc.getString("webhookUrl")
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

    override fun getAll(): List<UserRef> {
        val snapshots = firestore.collection("users").get().get()
        return snapshots.documents.map { FirestoreUserRef(it.reference) }
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
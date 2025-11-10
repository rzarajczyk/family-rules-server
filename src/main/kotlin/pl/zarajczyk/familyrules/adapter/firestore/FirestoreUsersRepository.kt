package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.*

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

    override fun fetchDetails(user: UserRef): UserDto {
        val doc = (user as FirestoreUserRef).doc.get().get()
        return UserDto(
            username = doc.getString("username") ?: throw RuntimeException("Unable to find username for given user"),
            passwordSha256 = doc.getString("passwordSha256") ?: throw RuntimeException("Unable to find password for given user"),
            accessLevel = doc.getString("accessLevel")?.let { AccessLevel.valueOf(it) } ?: throw RuntimeException("Unable to find access level for given user")
        )
    }

    override fun update(user: UserRef, newPasswordHash: String) {
        val userRef = (user as FirestoreUserRef).doc
        userRef.update("passwordSha256", newPasswordHash).get()
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
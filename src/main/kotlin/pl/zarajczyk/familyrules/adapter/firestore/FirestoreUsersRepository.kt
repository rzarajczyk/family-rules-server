package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.Firestore
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.*

@Service
class FirestoreUsersRepository(
    private val firestore: Firestore,
) : UsersRepository {
    override fun findUser(username: String): UserDto? {
        val doc = firestore.collection("users").document(username).get().get()
        return if (doc.exists()) {
            UserDto(
                username = doc.getString("username") ?: username,
                passwordSha256 = doc.getString("passwordSha256") ?: "",
                accessLevel = doc.getString("accessLevel")?.let { AccessLevel.valueOf(it) } ?: AccessLevel.ADMIN
            )
        } else null
    }

    @Throws(InvalidPassword::class)
    override fun validatePassword(username: String, password: String) {
        val user = findUser(username)
        if (user?.passwordSha256 != password.sha256()) {
            throw InvalidPassword()
        }
    }

    override fun changePassword(username: String, newPassword: String) {
        val userRef = firestore.collection("users").document(username)
        userRef.update("passwordSha256", newPassword.sha256()).get()
    }

    override fun getAllUsers(): List<UserDto> {
        return firestore.collection("users")
            .get()
            .get()
            .documents
            .map { doc ->
                UserDto(
                    username = doc.getString("username") ?: doc.id,
                    passwordSha256 = doc.getString("passwordSha256") ?: "",
                    accessLevel = doc.getString("accessLevel")?.let { AccessLevel.valueOf(it) } ?: AccessLevel.ADMIN
                )
            }
    }

    override fun deleteUser(username: String) {
        firestore.collection("users").document(username).delete().get()
    }

    override fun createUser(username: String, password: String, accessLevel: AccessLevel) {
        val userData = mapOf(
            "username" to username,
            "passwordSha256" to password.sha256(),
            "accessLevel" to accessLevel.name
        )

        firestore.collection("users").document(username).set(userData).get()
    }
}
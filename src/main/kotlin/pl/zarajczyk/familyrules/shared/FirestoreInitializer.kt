package pl.zarajczyk.familyrules.shared

import com.google.cloud.firestore.Firestore
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.properties.Delegates

@Component
@EnableConfigurationProperties(DatabaseInitializationProperties::class)
class FirestoreInitializer(
    private val firestore: Firestore,
    private val databaseInitializationProperties: DatabaseInitializationProperties
) {

    @EventListener(ApplicationReadyEvent::class)
    fun initializeDatabase(event: ApplicationReadyEvent) {
        if (databaseInitializationProperties.enabled) {
            val userDoc = firestore.collection("users")
                .document(databaseInitializationProperties.username)
                .get()
                .get()

            if (!userDoc.exists()) {
                firestore.collection("users")
                    .document(databaseInitializationProperties.username)
                    .set(mapOf(
                        "username" to databaseInitializationProperties.username,
                        "passwordSha256" to databaseInitializationProperties.password.sha256(),
                        "accessLevel" to AccessLevel.ADMIN.name
                    ))
                    .get()
            } else {
                // Update existing user to have admin access level if not already set
                val currentAccessLevel = userDoc.getString("accessLevel")
                if (currentAccessLevel == null) {
                    firestore.collection("users")
                        .document(databaseInitializationProperties.username)
                        .update("accessLevel", AccessLevel.ADMIN.name)
                        .get()
                }
            }
        }
    }
}

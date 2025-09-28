package pl.zarajczyk.familyrules.shared

import com.google.cloud.firestore.Firestore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import kotlin.properties.Delegates

@Component
@EnableConfigurationProperties(DatabaseInitializationProperties::class)
class FirestoreInitializer(
    private val firestore: Firestore,
    private val databaseInitializationProperties: DatabaseInitializationProperties
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
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
                        "passwordSha256" to databaseInitializationProperties.password.sha256()
                    ))
                    .get()
            }
        }
    }
}

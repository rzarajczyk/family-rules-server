package pl.zarajczyk.familyrules.shared

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream
import java.io.IOException

@Configuration
class FirestoreConfiguration {

    @Value("\${firestore.project-id:}")
    private lateinit var projectId: String

    @Value("\${firestore.service-account-path:}")
    private lateinit var serviceAccountPath: String

    @Bean
    fun firestore(): Firestore {
        val emulatorHost = System.getenv("FIRESTORE_EMULATOR_HOST")
        
        return if (emulatorHost != null) {
            // Use Firestore emulator
            FirestoreOptions.getDefaultInstance()
                .toBuilder()
                .setHost(emulatorHost)
                .setProjectId("demo-family-rules")
                .build()
                .service
        } else {
            // Use production Firestore
            val builder = FirestoreOptions.newBuilder()
                .setProjectId(projectId)
            
            if (serviceAccountPath.isNotEmpty()) {
                try {
                    val credentials = GoogleCredentials.fromStream(FileInputStream(serviceAccountPath))
                    builder.setCredentials(credentials)
                } catch (e: IOException) {
                    throw RuntimeException("Failed to load service account credentials from $serviceAccountPath", e)
                }
            }
            
            builder.build().service
        }
    }
}

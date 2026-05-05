package pl.zarajczyk.familyrules.configuration

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
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

            loadCredentialsIfConfigured()?.let(builder::setCredentials)

            builder.build().service
        }
    }

    @Bean
    fun storage(): Storage {
        val builder = StorageOptions.newBuilder()
            .setProjectId(projectId)

        loadCredentialsIfConfigured()?.let(builder::setCredentials)

        return builder.build().service
    }

    private fun loadCredentialsIfConfigured(): GoogleCredentials? {
        if (serviceAccountPath.isEmpty()) {
            return null
        }

        try {
            return GoogleCredentials.fromStream(FileInputStream(serviceAccountPath))
        } catch (e: IOException) {
            throw RuntimeException("Failed to load service account credentials from $serviceAccountPath", e)
        }
    }
}

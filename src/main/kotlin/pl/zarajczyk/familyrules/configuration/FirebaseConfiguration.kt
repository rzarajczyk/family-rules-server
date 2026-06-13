package pl.zarajczyk.familyrules.configuration

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FirebaseConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("\${firebase.project-id:}")
    private lateinit var projectId: String

    @PostConstruct
    fun initializeFirebaseIfConfigured() {
        if (projectId.isBlank()) {
            logger.info("firebase.project-id is not set; FCM force-report push is disabled")
            return
        }
        if (FirebaseApp.getApps().isNotEmpty()) {
            return
        }

        val credentials = loadCredentials()
        val optionsBuilder = FirebaseOptions.builder().setProjectId(projectId)
        credentials?.let(optionsBuilder::setCredentials)

        FirebaseApp.initializeApp(optionsBuilder.build())
        logger.info("Firebase initialized for project {}", projectId)
    }

    @Bean
    fun firebaseApp(): FirebaseApp? =
        FirebaseApp.getApps().firstOrNull()

    private fun loadCredentials(): GoogleCredentials? {
        val serviceAccountPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS").orEmpty()
        if (serviceAccountPath.isNotEmpty()) {
            return GoogleCredentials.fromStream(java.io.FileInputStream(serviceAccountPath))
        }
        return try {
            GoogleCredentials.getApplicationDefault()
        } catch (_: Exception) {
            null
        }
    }
}

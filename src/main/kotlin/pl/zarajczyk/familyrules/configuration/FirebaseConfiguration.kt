package pl.zarajczyk.familyrules.configuration

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import pl.zarajczyk.familyrules.adapter.fcm.FcmForceReportPushSender
import pl.zarajczyk.familyrules.domain.port.ForceReportPushSender

@Configuration
@Lazy(false)
class FirebaseConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("\${firebase.project-id:}")
    private lateinit var projectId: String

    @Bean
    @Lazy(false)
    fun firebaseApp(): FirebaseApp? {
        if (projectId.isBlank()) {
            logger.info("firebase.project-id is not set; FCM force-report push is disabled")
            return null
        }
        if (FirebaseApp.getApps().isNotEmpty()) {
            return FirebaseApp.getInstance()
        }

        val credentials = loadCredentials()
        val optionsBuilder = FirebaseOptions.builder().setProjectId(projectId)
        credentials?.let(optionsBuilder::setCredentials)

        val app = FirebaseApp.initializeApp(optionsBuilder.build())
        logger.info("Firebase initialized for project {}", projectId)
        return app
    }

    @Bean
    @Lazy(false)
    fun fcmForceReportPushSender(firebaseApp: ObjectProvider<FirebaseApp>): ForceReportPushSender? =
        firebaseApp.ifAvailable?.let { FcmForceReportPushSender() }

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

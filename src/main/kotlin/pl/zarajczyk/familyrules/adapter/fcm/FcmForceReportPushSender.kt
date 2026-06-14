package pl.zarajczyk.familyrules.adapter.fcm

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import pl.zarajczyk.familyrules.domain.Capability
import pl.zarajczyk.familyrules.domain.port.ForceReportPushResult
import pl.zarajczyk.familyrules.domain.port.ForceReportPushSender
import pl.zarajczyk.familyrules.domain.port.ForceReportPushStatus

@Component
@ConditionalOnBean(name = ["firebaseApp"])
class FcmForceReportPushSender(
    @Suppress("unused") firebaseApp: FirebaseApp,
) : ForceReportPushSender {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun supportedCapability(): String = Capability.FCM_FORCE_REPORT_PUSH

    override fun sendForceReport(pushToken: String): ForceReportPushResult {
        val message = Message.builder()
            .setToken(pushToken)
            .putData("action", "FORCE_REPORT")
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build()
            )
            .build()

        return try {
            FirebaseMessaging.getInstance().send(message)
            ForceReportPushResult(ForceReportPushStatus.SUCCESS)
        } catch (e: FirebaseMessagingException) {
            logger.warn("FCM force-report push failed: {}", e.message)
            val clearToken = e.messagingErrorCode == MessagingErrorCode.UNREGISTERED
                    || e.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT
            ForceReportPushResult(
                status = ForceReportPushStatus.PROVIDER_ERROR,
                message = e.message,
                clearStoredToken = clearToken,
            )
        } catch (e: Exception) {
            logger.warn("FCM force-report push failed", e)
            ForceReportPushResult(
                status = ForceReportPushStatus.PROVIDER_ERROR,
                message = e.message,
            )
        }
    }
}

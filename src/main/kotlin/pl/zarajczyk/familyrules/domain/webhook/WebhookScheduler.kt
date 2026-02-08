package pl.zarajczyk.familyrules.domain.webhook

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.configuration.WebhookProperties
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Scheduler that periodically checks for users with recent activity and webhooks enabled,
 * then enqueues them for webhook notification.
 */
@Service
@EnableConfigurationProperties(WebhookProperties::class)
class WebhookScheduler(
    private val usersRepository: UsersRepository,
    private val webhookQueue: WebhookQueue,
    private val webhookProperties: WebhookProperties
) {
    private val logger = LoggerFactory.getLogger(WebhookScheduler::class.java)

    /**
     * Runs periodically to check for users with recent activity
     * and enqueues them for webhook notification.
     * Interval is configurable via webhook.scheduler-interval-millis property.
     */
    @Scheduled(fixedDelayString = "\${webhook.scheduler-interval-millis:30000}")
    fun scheduleWebhookNotifications() {
        val activityWindow = Clock.System.now() - webhookProperties.schedulerIntervalMillis.milliseconds * 2
        enqueueUsersWithRecentActivity(activityWindow, "webhook")
    }

    /**
     * Runs daily at 00:01 to send updates to all users with activity in the previous 24 hours.
     * This is needed to reset screen time counters on the client side at midnight.
     */
    @Scheduled(cron = "0 1 0 * * *")
    fun scheduleMidnightWebhookNotifications() {
        val activityWindow = Clock.System.now() - 24.hours
        enqueueUsersWithRecentActivity(activityWindow, "midnight webhook to reset screen times")
    }

    private fun enqueueUsersWithRecentActivity(activityWindow: Instant, notificationType: String) {
        try {
            val usersWithRecentActivity = usersRepository.getUsersWithRecentActivity(activityWindow)
            
            usersWithRecentActivity.forEach { userRef ->
                val userDetails = usersRepository.fetchDetails(userRef)
                logger.debug("Enqueueing user {} for {} notification", userDetails.username, notificationType)
                webhookQueue.enqueue(userDetails.username)
            }
            
            if (usersWithRecentActivity.isNotEmpty()) {
                logger.info("Enqueued {} users for {} notifications", usersWithRecentActivity.size, notificationType)
            } else {
                logger.debug("No users with recent activity for {} notifications", notificationType)
            }
        } catch (e: Exception) {
            logger.error("Error scheduling {} notifications", notificationType, e)
        }
    }
}

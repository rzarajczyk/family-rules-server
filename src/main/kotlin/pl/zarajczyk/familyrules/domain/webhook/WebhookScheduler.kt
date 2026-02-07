package pl.zarajczyk.familyrules.domain.webhook

import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.configuration.WebhookProperties
import pl.zarajczyk.familyrules.domain.port.UsersRepository
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
        val activityWindow = Clock.System.now() - webhookProperties.schedulerIntervalMillis.milliseconds
        
        try {
            val usersWithRecentActivity = usersRepository.getUsersWithRecentActivity(activityWindow)
            
            usersWithRecentActivity.forEach { userRef ->
                val userDetails = usersRepository.fetchDetails(userRef)
                logger.debug("Enqueueing user {} for webhook notification", userDetails.username)
                webhookQueue.enqueue(userDetails.username)
            }
            
            if (usersWithRecentActivity.isNotEmpty()) {
                logger.info("Enqueued {} users for webhook notifications", usersWithRecentActivity.size)
            }
        } catch (e: Exception) {
            logger.error("Error scheduling webhook notifications", e)
        }
    }
}

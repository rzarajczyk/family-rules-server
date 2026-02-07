package pl.zarajczyk.familyrules.domain.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.datetime.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.gui.bff.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Processes webhook notifications from the queue continuously.
 * Computes combined status and sends webhook notifications for users.
 */
@Service
class WebhookProcessor(
    private val webhookQueue: WebhookQueue,
    private val webhookClient: WebhookClient,
    private val usersService: UsersService,
    private val devicesService: DevicesService,
    private val stateService: StateService,
    private val appGroupService: AppGroupService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(WebhookProcessor::class.java)
    private val running = AtomicBoolean(false)
    private var processorThread: Thread? = null

    @PostConstruct
    fun start() {
        running.set(true)
        processorThread = Thread(::processQueue, "webhook-processor").apply {
            isDaemon = true
            start()
        }
        logger.info("WebhookProcessor started")
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        processorThread?.interrupt()
        logger.info("WebhookProcessor stopped")
    }

    private fun processQueue() {
        while (running.get()) {
            try {
                val username = webhookQueue.dequeue()
                if (username != null) {
                    processWebhookForUser(username)
                } else {
                    // Queue is empty, sleep for a bit
                    Thread.sleep(1000)
                }
            } catch (e: InterruptedException) {
                logger.debug("WebhookProcessor interrupted")
                break
            } catch (e: Exception) {
                logger.error("Error processing webhook queue", e)
                // Continue processing even after errors
            }
        }
    }

    private fun processWebhookForUser(username: String) {
        try {
            logger.debug("Processing webhook for user: {}", username)
            
            val user = usersService.get(username)
            val userDetails = user.fetchDetails()
            
            if (!userDetails.webhookEnabled || userDetails.webhookUrl == null) {
                logger.warn("User {} has webhookEnabled=false or no webhookUrl, skipping", username)
                return
            }

            val today = today()
            val payload = computeWebhookPayload(user, today)
            val jsonPayload = objectMapper.writeValueAsString(payload)
            
            webhookClient.sendWebhook(userDetails.webhookUrl, jsonPayload)
            logger.info("Webhook sent successfully for user: {}", username)
            
        } catch (e: Exception) {
            logger.error("Failed to send webhook for user: {}", username, e)
            // Don't retry, just log and continue
        }
    }

    private fun computeWebhookPayload(user: User, date: LocalDate): WebhookPayload {
        val devices = devicesService.getAllDevices(user)
        val appGroups = appGroupService.listAllAppGroups(user)
        
        // Compute status similar to BffOverviewController.status
        val deviceStatuses = devices.map { device ->
            val screenTimeDto = device.getScreenTimeReport(date)
            val state = stateService.calculateCurrentDeviceState(device)
            val deviceDetails = device.fetchDetails()
            
            DeviceStatus(
                deviceId = deviceDetails.deviceId.toString(),
                deviceName = deviceDetails.deviceName,
                screenTimeSeconds = screenTimeDto.screenTimeSeconds,
                online = screenTimeDto.online,
                forcedDeviceState = state.forcedState?.deviceState,
                automaticDeviceState = state.automaticState.deviceState
            )
        }
        
        // Compute app group statistics similar to BffAppGroupsController.getAppGroupStatistics
        val appGroupReport = appGroupService.getReport(user, date)
        val appGroupStatistics = appGroupReport.map { group ->
            AppGroupStatus(
                id = group.id,
                name = group.name,
                color = group.color,
                appsCount = group.appsCount,
                devicesCount = group.devicesCount,
                totalScreenTime = group.totalScreenTime,
                online = group.online
            )
        }
        
        return WebhookPayload(
            date = date.toString(),
            devices = deviceStatuses,
            appGroups = appGroupStatistics
        )
    }
}

data class WebhookPayload(
    val date: String,
    val devices: List<DeviceStatus>,
    val appGroups: List<AppGroupStatus>
)

data class DeviceStatus(
    val deviceId: String,
    val deviceName: String,
    val screenTimeSeconds: Long,
    val online: Boolean,
    val forcedDeviceState: String?,
    val automaticDeviceState: String
)

data class AppGroupStatus(
    val id: String,
    val name: String,
    val color: String,
    val appsCount: Int,
    val devicesCount: Int,
    val totalScreenTime: Long,
    val online: Boolean
)

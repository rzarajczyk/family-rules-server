package pl.zarajczyk.familyrules.domain.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.datetime.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import pl.zarajczyk.familyrules.configuration.WebhookProperties
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import pl.zarajczyk.familyrules.domain.port.WebhookCallHistoryEntry
import pl.zarajczyk.familyrules.gui.bff.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Processes webhook notifications from the queue continuously.
 * Computes combined status and sends webhook notifications for users.
 */
@Service
@Lazy(false)
@EnableConfigurationProperties(WebhookProperties::class)
class WebhookProcessor(
    private val webhookQueue: WebhookQueue,
    private val webhookClient: WebhookClient,
    private val usersService: UsersService,
    private val usersRepository: UsersRepository,
    private val appGroupService: AppGroupService,
    private val groupStateService: GroupStateService,
    private val objectMapper: ObjectMapper,
    private val webhookProperties: WebhookProperties
) {
    private val logger = LoggerFactory.getLogger(WebhookProcessor::class.java)
    private val running = AtomicBoolean(false)
    private lateinit var executorService: ExecutorService

    @PostConstruct
    fun start() {
        running.set(true)
        
        // Create thread pool executor with configurable size
        executorService = ThreadPoolExecutor(
            webhookProperties.processorThreads,
            webhookProperties.processorThreads,
            0L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue()
        ) { runnable ->
            Thread(runnable, "webhook-processor-${System.nanoTime()}").apply {
                isDaemon = true
            }
        }

        // Submit worker tasks - each thread continuously processes the queue
        repeat(webhookProperties.processorThreads) {
            executorService.submit(::processQueue)
        }
        
        logger.info("WebhookProcessor started with {} worker thread(s)", webhookProperties.processorThreads)
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
        }
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
                    Thread.sleep(500)
                }
            } catch (e: InterruptedException) {
                logger.debug("WebhookProcessor worker thread interrupted")
                break
            } catch (e: Exception) {
                logger.error("Error processing webhook queue", e)
                // Continue processing even after errors
            }
        }
    }

    private fun processWebhookForUser(username: String) {
        var statusCode: Int? = null
        var errorMessage: String? = null
        var status = "success"
        
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
            
            try {
                webhookClient.sendWebhook(userDetails.webhookUrl, jsonPayload)
                statusCode = 200
                logger.info("Webhook sent successfully for user: {}", username)
            } catch (e: HttpStatusCodeException) {
                status = "error"
                statusCode = e.statusCode.value()
                errorMessage = "HTTP ${e.statusCode.value()}: ${e.statusText}"
                logger.error("HTTP error sending webhook for user: {}", username, e)
            } catch (e: Exception) {
                status = "error"
                errorMessage = e.message ?: "Unknown error"
                logger.error("Failed to send webhook for user: {}", username, e)
            } finally {
                // Record the call history
                val historyEntry = WebhookCallHistoryEntry(
                    timestamp = System.currentTimeMillis(),
                    status = status,
                    statusCode = statusCode,
                    errorMessage = errorMessage,
                    payload = if (status == "success") jsonPayload else null
                )
                usersRepository.addWebhookCallHistory(user.asRef(), historyEntry)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to process webhook for user: {}", username, e)
        }
    }

    private fun computeWebhookPayload(user: User, date: LocalDate): WebhookPayload {
//        val devices = devicesService.getAllDevices(user)
//        val appGroups = appGroupService.listAllAppGroups(user)
        
        // Compute status similar to BffOverviewController.status
//        val deviceStatuses = devices.map { device ->
//            val screenTimeDto = device.getScreenTimeReport(date)
//            val state = stateService.calculateCurrentDeviceState(device)
//            val deviceDetails = device.fetchDetails()
//
//            DeviceStatus(
//                deviceId = deviceDetails.deviceId.toString(),
//                deviceName = deviceDetails.deviceName,
//                screenTimeSeconds = screenTimeDto.screenTimeSeconds,
//                online = screenTimeDto.online,
//                forcedDeviceState = state.forcedState?.deviceState,
//                automaticDeviceState = state.automaticState.deviceState
//            )
//        }
        val deviceStatuses = emptyList<DeviceStatus>() // not used currently!
        
        // Compute app group statistics similar to BffAppGroupsController.getAppGroupStatistics
        val appGroupReport = appGroupService.getReport(user, date)
        val appGroups = appGroupService.listAllAppGroups(user)
        
        val appGroupStatistics = appGroupReport.map { group ->
            // Find the corresponding AppGroup to get available states
            val appGroup = appGroups.find { it.fetchDetails().id == group.id }
            val availableStates = appGroup?.let { ag ->
                groupStateService.listAllGroupStates(ag).map { state ->
                    val details = state.fetchDetails()
                    GroupStateInfo(
                        id = details.id,
                        name = details.name
                    )
                }
            } ?: emptyList()
            
            AppGroupStatus(
                id = group.id,
                name = group.name,
                color = group.color,
                appsCount = group.appsCount,
                devicesCount = group.devicesCount,
                totalScreenTime = group.totalScreenTime,
                online = group.online,
                availableStates = availableStates
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
    val online: Boolean,
    val availableStates: List<GroupStateInfo>
)

data class GroupStateInfo(
    val id: String,
    val name: String
)

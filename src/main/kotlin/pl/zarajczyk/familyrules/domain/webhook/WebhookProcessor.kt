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
import pl.zarajczyk.familyrules.domain.port.AppGroupDto
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import pl.zarajczyk.familyrules.domain.port.WebhookCallHistoryEntry
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

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
    private val usersRepository: UsersRepository,
    private val appGroupService: AppGroupService,
    private val devicesService: DevicesService,
    private val stateService: StateService,
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
                val user = webhookQueue.take(500, TimeUnit.MILLISECONDS)
                if (user != null) {
                    processWebhookForUser(user)
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

    fun processWebhookForUser(user: User) {
        val userDetails = user.getDetails()
        val username = userDetails.username
        var statusCode: Int? = null
        var errorMessage: String? = null
        var status = "success"

        try {
            logger.info("Processing webhook for user: {}", username)

            if (!userDetails.webhookEnabled || userDetails.webhookUrl == null) {
                logger.warn("User {} has webhookEnabled=false or no webhookUrl, skipping", username)
                return
            }

            val today = today()
            val payloadStartedAt = System.nanoTime()
            val payload = computeWebhookPayload(user, today)
            val payloadDurationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - payloadStartedAt)
            val jsonPayload = objectMapper.writeValueAsString(payload)
            val brief = payload.appGroups.joinToString(", ") { it.name + "=" + it.currentState.label }

            logger.info("Webhook payload calculated for user: {} in {} ms - {}", username, payloadDurationMillis, brief)

            try {
                val sendStartedAt = System.nanoTime()
                webhookClient.sendWebhook(userDetails.webhookUrl, jsonPayload)
                val sendDurationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sendStartedAt)
                statusCode = 200
                logger.info("Webhook sent successfully for user: {} in {} ms - {}", username, sendDurationMillis, brief)
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
                // Record the call history only if history is currently enabled
                val historyUntil = userDetails.webhookHistoryUntil
                if (historyUntil != null && System.currentTimeMillis() <= historyUntil) {
                    val historyEntry = WebhookCallHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        status = status,
                        statusCode = statusCode,
                        errorMessage = errorMessage,
                        payload = if (status == "success") jsonPayload else null
                    )
                    usersRepository.addWebhookCallHistory(user.asRef(), historyEntry)
                }
            }

        } catch (e: Exception) {
            logger.error("Failed to process webhook for user: {}", username, e)
        }
    }

    private fun computeWebhookPayload(user: User, date: LocalDate): WebhookPayload {
        val allDevices = devicesService.getAllDevices(user)

        val simplifiedReport = appGroupService.getSimplifiedReport(user, date, allDevices)

        val deviceForcedStates: Map<DeviceId, DeviceStateDto?> =
            allDevices.associate {
                it.getId() to it.getDetails().forcedDeviceState
            }

        val appGroupStatistics = simplifiedReport.map { report ->
            val currentState = calculateCurrentGroupState(report.groupDto, deviceForcedStates)
            AppGroupStatus(
                id = report.id,
                name = report.name,
                online = report.online,
                totalScreenTime = report.totalScreenTimeSeconds,
                currentState = currentState,
            )
        }

        return WebhookPayload(
            date = date.toString(),
            appGroups = appGroupStatistics,
        )
    }

    private fun calculateCurrentGroupState(
        groupDto: AppGroupDto,
        deviceForcedStates: Map<DeviceId, DeviceStateDto?>,
    ): AppGroupCurrentState {
        val stateDetails = groupDto.states.values.toList()
        val groupDeviceIds = stateDetails.flatMap { it.deviceStates.keys }.toSet()

        if (groupDeviceIds.isEmpty()) {
            return AppGroupCurrentState(kind = "automatic", label = "Automatic", stateId = null)
        }

        val matched = stateDetails.firstOrNull { details ->
            details.deviceStates.all { (deviceId, expectedState) ->
                deviceForcedStates[deviceId] == expectedState
            }
        }

        if (matched != null) {
            return AppGroupCurrentState(kind = "named", label = matched.name, stateId = matched.id)
        }

        val allAutomatic = groupDeviceIds.all { deviceId -> deviceForcedStates[deviceId] == null }

        return if (allAutomatic) {
            AppGroupCurrentState(kind = "automatic", label = "Automatic", stateId = null)
        } else {
            AppGroupCurrentState(kind = "different", label = "Different", stateId = null)
        }
    }
}

data class WebhookPayload(
    val date: String,
    val appGroups: List<AppGroupStatus>
)

data class AppGroupStatus(
    val id: String,
    val name: String,
    val online: Boolean,
    val totalScreenTime: Long,
    val currentState: AppGroupCurrentState,
)

data class AppGroupCurrentState(
    val kind: String,   // "named" | "automatic" | "different"
    val label: String,
    val stateId: String?,
)

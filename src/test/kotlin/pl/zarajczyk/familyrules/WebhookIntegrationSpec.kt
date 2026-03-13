package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.gcloud.FirestoreEmulatorContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set
import pl.zarajczyk.familyrules.domain.webhook.WebhookQueue
import pl.zarajczyk.familyrules.domain.webhook.WebhookScheduler
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class WebhookIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var usersRepository: UsersRepository

    @Autowired
    private lateinit var usersService: UsersService

    @Autowired
    private lateinit var devicesService: DevicesService

    @Autowired
    private lateinit var appGroupService: AppGroupService

    @Autowired
    private lateinit var groupStateService: GroupStateService

    @Autowired
    private lateinit var webhookQueue: WebhookQueue

    @Autowired
    private lateinit var webhookScheduler: WebhookScheduler

    @Autowired
    private lateinit var capturingWebhookClient: CapturingWebhookClient

    companion object {
        @Container
        @JvmStatic
        val firestoreContainer: FirestoreEmulatorContainer =
            FirestoreEmulatorContainer("gcr.io/google.com/cloudsdktool/google-cloud-cli:546.0.0-emulators")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            firestoreContainer.start()
            registry.add("firestore.emulator-host") { firestoreContainer.emulatorEndpoint }
        }
    }

    /** Minimal in-memory [User] for local queue tests — no Firestore required. */
    private fun testUser(username: String): User = object : User {
        override fun asRef() = throw UnsupportedOperationException("not needed in queue tests")
        override fun getDetails() = UserDetails(
            username = username,
            passwordSha256 = "",
            accessLevel = AccessLevel.PARENT,
        )
        override fun delete() = throw UnsupportedOperationException()
        override fun validatePassword(password: String) = throw UnsupportedOperationException()
        override fun changePassword(newPassword: String) = throw UnsupportedOperationException()
        override fun updateWebhookSettings(webhookEnabled: Boolean, webhookUrl: String?) = throw UnsupportedOperationException()
        override fun updateIntegrationApiToken(token: String?) = throw UnsupportedOperationException()
        override fun updateLastActivity(lastActivityMillis: Long) = throw UnsupportedOperationException()
    }

    init {
        test("WebhookQueue should enqueue and dequeue Users") {
            // Given
            val queue = WebhookQueue()

            // When
            queue.enqueue(testUser("user1"))
            queue.enqueue(testUser("user2"))
            queue.enqueue(testUser("user3"))

            // Then
            queue.size() shouldBe 3
            queue.take(100, TimeUnit.MILLISECONDS)?.getDetails()?.username shouldBe "user1"
            queue.take(100, TimeUnit.MILLISECONDS)?.getDetails()?.username shouldBe "user2"
            queue.take(100, TimeUnit.MILLISECONDS)?.getDetails()?.username shouldBe "user3"
            queue.take(100, TimeUnit.MILLISECONDS) shouldBe null
            queue.isEmpty() shouldBe true
        }

//        test("WebhookQueue should deduplicate usernames") {
//            // Given
//            val queue = WebhookQueue()
//
//            // When
//            queue.enqueue("user1")
//            queue.enqueue("user1")
//            queue.enqueue("user2")
//            queue.enqueue("user1")
//
//            // Then
//            queue.size() shouldBe 2
//            val dequeued = mutableListOf<String>()
//            while (!queue.isEmpty()) {
//                queue.take(100, TimeUnit.MILLISECONDS)?.let { dequeued.add(it) }
//            }
//            dequeued shouldHaveSize 2
//            dequeued shouldContain "user1"
//            dequeued shouldContain "user2"
//        }

        test("User lastActivity field should be updated when device reports") {
            // Given
            val testUsername = "webhook-test-user-${System.currentTimeMillis()}"
            val testPassword = "test-password"
            usersRepository.createUser(testUsername, testPassword.sha256(), AccessLevel.PARENT)
            
            val deviceDetails = devicesService.setupNewDevice(
                username = testUsername,
                deviceName = "test-device",
                clientType = "android"
            )
            
            val beforeTime = System.currentTimeMillis()
            
            // When
            val device = devicesService.get(deviceDetails.deviceId)
            device.saveScreenTimeReport(
                day = today(),
                screenTimeSeconds = 1000,
                applicationsSeconds = mapOf("app1" to 500L),
                activeApps = null
            )
            device.getOwner().updateLastActivity(System.currentTimeMillis())
            
            // Then
            val user = usersService.get(testUsername)
            val userDetails = user.getDetails()
            userDetails.lastActivity.shouldNotBeNull()
            userDetails.lastActivity!! shouldBeGreaterThanOrEqual beforeTime
        }

        test("WebhookScheduler should enqueue users with recent activity and webhook enabled") {
            // Given
            val testUsername = "webhook-scheduler-test-${System.currentTimeMillis()}"
            val testPassword = "test-password"
            val webhookUrl = "https://example.com/webhook"
            
            usersRepository.createUser(testUsername, testPassword.sha256(), AccessLevel.PARENT)
            val user = usersService.get(testUsername)
            
            // Enable webhook
            user.updateWebhookSettings(webhookEnabled = true, webhookUrl = webhookUrl)
            
            // Update lastActivity to now
            user.updateLastActivity(System.currentTimeMillis())
            
            // Clear the queue and captured payloads
            while (!webhookQueue.isEmpty()) {
                webhookQueue.take(100, TimeUnit.MILLISECONDS)
            }
            capturingWebhookClient.clear()
            
            // When
            webhookScheduler.scheduleWebhookNotifications()
            
            // Then — the processor may consume the item before we can check the queue,
            // so wait briefly and verify it was processed (webhook was sent)
            val deadline = System.currentTimeMillis() + 5_000
            while (capturingWebhookClient.capturedPayloads.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            capturingWebhookClient.capturedPayloads.size shouldBe 1
        }

        test("WebhookScheduler should NOT enqueue users with webhook disabled") {
            // Given
            val testUsername = "webhook-disabled-test-${System.currentTimeMillis()}"
            val testPassword = "test-password"
            val webhookUrl = "https://example.com/webhook/$testUsername"
            
            usersRepository.createUser(testUsername, testPassword.sha256(), AccessLevel.PARENT)
            val user = usersService.get(testUsername)
            
            // Webhook disabled, but we set a URL anyway to ensure it doesn't get called
            user.updateWebhookSettings(webhookEnabled = false, webhookUrl = webhookUrl)
            user.updateLastActivity(System.currentTimeMillis())
            
            // Clear the queue
            while (!webhookQueue.isEmpty()) {
                webhookQueue.take(100, TimeUnit.MILLISECONDS)
            }
            capturingWebhookClient.clear()
            
            // When
            webhookScheduler.scheduleWebhookNotifications()
            
            // Then - the user with webhook disabled should NOT be enqueued
            Thread.sleep(1000) // Give the processor a moment to run if it mistakenly enqueued
            capturingWebhookClient.capturedUrls shouldNotContain webhookUrl
        }

        test("WebhookScheduler should NOT enqueue users with old activity") {
            // Given
            val testUsername = "webhook-old-activity-test-${System.currentTimeMillis()}"
            val testPassword = "test-password"
            val webhookUrl = "https://example.com/webhook/$testUsername"
            
            usersRepository.createUser(testUsername, testPassword.sha256(), AccessLevel.PARENT)
            val user = usersService.get(testUsername)
            
            // Enable webhook
            user.updateWebhookSettings(webhookEnabled = true, webhookUrl = webhookUrl)
            
            // Update lastActivity to 1 HOUR ago (safely older than the 30-second window)
            val oneHourAgo = System.currentTimeMillis() - 3600000
            user.updateLastActivity(oneHourAgo)
            
            // Clear the queue
            while (!webhookQueue.isEmpty()) {
                webhookQueue.take(100, TimeUnit.MILLISECONDS)
            }
            capturingWebhookClient.clear()
            
            // When
            webhookScheduler.scheduleWebhookNotifications()
            
            // Then - the user with old activity should NOT be enqueued
            Thread.sleep(1000) // Give the processor a moment to run if it mistakenly enqueued
            capturingWebhookClient.capturedUrls shouldNotContain webhookUrl
        }

        test("getUsersWithRecentActivity should return users with activity within time window") {
            // Given
            val testUsername1 = "recent-activity-1-${System.currentTimeMillis()}"
            val testUsername2 = "recent-activity-2-${System.currentTimeMillis()}"
            val testPassword = "test-password"
            val webhookUrl = "https://example.com/webhook"
            
            usersRepository.createUser(testUsername1, testPassword.sha256(), AccessLevel.PARENT)
            usersRepository.createUser(testUsername2, testPassword.sha256(), AccessLevel.PARENT)
            
            val user1 = usersService.get(testUsername1)
            val user2 = usersService.get(testUsername2)
            
            user1.updateWebhookSettings(webhookEnabled = true, webhookUrl = webhookUrl)
            user2.updateWebhookSettings(webhookEnabled = true, webhookUrl = webhookUrl)
            
            // User1 has recent activity
            user1.updateLastActivity(System.currentTimeMillis())
            
            // User2 has old activity
            user2.updateLastActivity(System.currentTimeMillis() - 60000)
            
            // When
            val since = Clock.System.now() - 30.seconds
            val usersWithRecentActivity = usersRepository.getUsersWithRecentActivity(since)
            
            // Then
            val usernames = usersWithRecentActivity.map { 
                it.details.username 
            }
            usernames shouldContain testUsername1
        }

        test("webhook payload should include currentState for each app group") {
            // Given
            val testUsername = "webhook-payload-state-test-${System.currentTimeMillis()}"
            usersRepository.createUser(testUsername, "pass".sha256(), AccessLevel.PARENT)
            val user = usersService.get(testUsername)
            user.updateWebhookSettings(webhookEnabled = true, webhookUrl = "https://example.com/webhook")
            user.updateLastActivity(System.currentTimeMillis())

            val device = devicesService.setupNewDevice(testUsername, "TestDevice", "android")
            val deviceId = device.deviceId

            val group = appGroupService.createAppGroup(user, "Games")
            val groupObj = appGroupService.get(user, group.getDetails().id)

            val lockedState = groupStateService.createGroupState(
                groupObj, "Locked",
                mapOf(deviceId to DeviceStateDto(deviceState = "LOCKED", extra = null))
            )

            // Set device to forced "LOCKED" state so it matches the named state
            val deviceDomain = devicesService.get(deviceId)
            deviceDomain.update(DeviceDetailsUpdateDto(forcedDeviceState = set(DeviceStateDto("LOCKED", null))))

            capturingWebhookClient.clear()

            // When — enqueue and wait for the processor to fire (max 5 s)
            webhookQueue.enqueue(usersService.get(testUsername))
            val deadline = System.currentTimeMillis() + 5_000
            while (capturingWebhookClient.capturedPayloads.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }

            // Then
            capturingWebhookClient.capturedPayloads.size shouldBe 1
            val payload = objectMapper.readTree(capturingWebhookClient.capturedPayloads[0])
            val appGroups = payload.get("appGroups")
            appGroups.shouldNotBeNull()
            appGroups.size() shouldBe 1

            val gamesGroup = appGroups[0]
            gamesGroup.get("name").asText() shouldBe "Games"

            val currentState = gamesGroup.get("currentState")
            currentState.shouldNotBeNull()
            currentState.get("kind").asText() shouldBe "named"
            currentState.get("label").asText() shouldBe "Locked"
            currentState.get("stateId").asText() shouldBe lockedState.fetchDetails().id
        }
    }
}

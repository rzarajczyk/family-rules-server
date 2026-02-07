package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
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
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import pl.zarajczyk.familyrules.domain.webhook.WebhookQueue
import pl.zarajczyk.familyrules.domain.webhook.WebhookScheduler
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
    private lateinit var webhookQueue: WebhookQueue

    @Autowired
    private lateinit var webhookScheduler: WebhookScheduler

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

    init {
        test("WebhookQueue should enqueue and dequeue usernames") {
            // Given
            val queue = WebhookQueue()
            
            // When
            queue.enqueue("user1")
            queue.enqueue("user2")
            queue.enqueue("user3")
            
            // Then
            queue.size() shouldBe 3
            queue.dequeue() shouldBe "user1"
            queue.dequeue() shouldBe "user2"
            queue.dequeue() shouldBe "user3"
            queue.dequeue() shouldBe null
            queue.isEmpty() shouldBe true
        }

        test("WebhookQueue should deduplicate usernames") {
            // Given
            val queue = WebhookQueue()
            
            // When
            queue.enqueue("user1")
            queue.enqueue("user1")
            queue.enqueue("user2")
            queue.enqueue("user1")
            
            // Then
            queue.size() shouldBe 2
            val dequeued = mutableListOf<String>()
            while (!queue.isEmpty()) {
                queue.dequeue()?.let { dequeued.add(it) }
            }
            dequeued shouldHaveSize 2
            dequeued shouldContain "user1"
            dequeued shouldContain "user2"
        }

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
                applicationsSeconds = mapOf("app1" to 500L)
            )
            device.getOwner().updateLastActivity(System.currentTimeMillis())
            
            // Then
            val user = usersService.get(testUsername)
            val userDetails = user.fetchDetails()
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
            
            // Clear the queue
            while (!webhookQueue.isEmpty()) {
                webhookQueue.dequeue()
            }
            
            // When
            webhookScheduler.scheduleWebhookNotifications()
            
            // Then
            webhookQueue.isEmpty() shouldBe false
            val dequeuedUser = webhookQueue.dequeue()
            dequeuedUser shouldBe testUsername
        }

        test("WebhookScheduler should NOT enqueue users with webhook disabled") {
            // Given
            val testUsername = "webhook-disabled-test-${System.currentTimeMillis()}"
            val testPassword = "test-password"
            
            usersRepository.createUser(testUsername, testPassword.sha256(), AccessLevel.PARENT)
            val user = usersService.get(testUsername)
            
            // Webhook disabled
            user.updateWebhookSettings(webhookEnabled = false, webhookUrl = null)
            user.updateLastActivity(System.currentTimeMillis())
            
            // Clear the queue
            while (!webhookQueue.isEmpty()) {
                webhookQueue.dequeue()
            }
            
            // When
            webhookScheduler.scheduleWebhookNotifications()
            
            // Then - the user with webhook disabled should NOT be enqueued
            val dequeuedUsers = mutableListOf<String>()
            while (!webhookQueue.isEmpty()) {
                webhookQueue.dequeue()?.let { dequeuedUsers.add(it) }
            }
            dequeuedUsers shouldNotContain testUsername
        }

        test("WebhookScheduler should NOT enqueue users with old activity") {
            // Given
            val testUsername = "webhook-old-activity-test-${System.currentTimeMillis()}"
            val testPassword = "test-password"
            val webhookUrl = "https://example.com/webhook"
            
            usersRepository.createUser(testUsername, testPassword.sha256(), AccessLevel.PARENT)
            val user = usersService.get(testUsername)
            
            // Enable webhook
            user.updateWebhookSettings(webhookEnabled = true, webhookUrl = webhookUrl)
            
            // Update lastActivity to 1 minute ago (older than the 30-second window)
            val oneMinuteAgo = System.currentTimeMillis() - 60000
            user.updateLastActivity(oneMinuteAgo)
            
            // Clear the queue
            while (!webhookQueue.isEmpty()) {
                webhookQueue.dequeue()
            }
            
            // When
            webhookScheduler.scheduleWebhookNotifications()
            
            // Then - the user with old activity should NOT be enqueued
            val dequeuedUsers = mutableListOf<String>()
            while (!webhookQueue.isEmpty()) {
                webhookQueue.dequeue()?.let { dequeuedUsers.add(it) }
            }
            dequeuedUsers shouldNotContain testUsername
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
                usersRepository.fetchDetails(it).username 
            }
            usernames shouldContain testUsername1
        }
    }
}

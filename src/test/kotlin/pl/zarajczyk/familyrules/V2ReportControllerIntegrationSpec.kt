package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.gcloud.FirestoreEmulatorContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import java.util.Base64
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class V2ReportControllerIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var usersRepository: UsersRepository

    @Autowired
    private lateinit var devicesService: DevicesService

    @Autowired
    private lateinit var devicesRepository: DevicesRepository

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
        val username = "report-user-${System.currentTimeMillis()}"
        val password = "report-pass"
        val deviceName = "Report Test Device"
        val clientType = "TEST"

        lateinit var deviceId: UUID
        lateinit var token: String

        beforeSpec {
            usersRepository.createUser(username, password.sha256(), AccessLevel.PARENT)
            val device = devicesService.setupNewDevice(username, deviceName, clientType)
            deviceId = device.deviceId
            token = device.token
        }

        afterSpec {
            devicesRepository.get(deviceId)?.also { devicesRepository.delete(it) }
            usersRepository.get(username)?.also { usersRepository.delete(it) }
        }

        test("should accept report, persist screenTime and return deviceState") {
            val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            val reportBody = """
                {
                  "screenTime": 900,
                  "applications": {
                    "com.example.app1": 600,
                    "com.example.app2": 300
                  }
                }
            """.trimIndent()

            val result = mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reportBody)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.deviceState").exists())
                .andReturn()

            // Verify database side-effects
            val deviceRef = devicesRepository.get(deviceId)!!
            val today = today()
            val screenTimes = devicesRepository.getScreenReport(deviceRef, today)
            screenTimes.shouldNotBeNull()
            screenTimes.screenTimeSeconds shouldBe 900L
            screenTimes.applicationsSeconds["com.example.app1"] shouldBe 600L
            screenTimes.applicationsSeconds["com.example.app2"] shouldBe 300L
            screenTimes.updatedAt shouldNotBe null
            val histogram = screenTimes.screenTimeHistogram
            histogram.isNotEmpty() shouldBe true
            histogram.values.sum() shouldBe 1L
            val expectedBucket = histogramBucketFor(screenTimes.updatedAt)
            histogram[expectedBucket] shouldBe 1L
        }

        test("should return 400 on invalid JSON") {
            val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            val invalidJson = "{"

            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson)
            )
                .andExpect(status().isBadRequest)
        }

    test("should fail with 401 when Authorization header missing or malformed") {
            val reportBody = """
                { "screenTime": 10, "applications": {} }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reportBody)
            )
                .andExpect(status().isUnauthorized)
        }
    }
}

private fun histogramBucketFor(updatedAt: Instant): String {
    val localDateTime = updatedAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minuteBucket = (localDateTime.minute / 10) * 10
    val minute = minuteBucket.toString().padStart(2, '0')
    return "$hour:$minute"
}

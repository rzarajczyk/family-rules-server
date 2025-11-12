package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
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
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.InstanceRef
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import pl.zarajczyk.familyrules.domain.today
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class HappyPathIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var devicesRepository: DevicesRepository

    @Autowired
    private lateinit var usersRepository: UsersRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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
        // Shared state across test steps
        val username = "admin"
        val password = "admin"
        val instanceName = "test-device-${System.currentTimeMillis()}"
        val clientType = "TEST"
        val clientVersion = "v1.0.0"
        val timezoneOffsetSeconds = 3600
        val reportIntervalSeconds = 60
        
        var instanceId: String = ""
        var token: String = ""
        lateinit var instanceRef: InstanceRef
        lateinit var firstReportTimestamp: Instant

        test("step 1 - should verify default user exists in database") {
            val userRef = usersRepository.get(username)
            userRef shouldNotBe null
            val user = usersRepository.fetchDetails(userRef!!)
            user.username shouldBe username
            user.passwordSha256 shouldNotBe null
        }

        test("step 2 - should register instance successfully") {
            val basic = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            
            val registerResponse = mockMvc.perform(
                post("/api/v2/register-instance")
                    .header("Authorization", "Basic $basic")
                    .contentType("application/json")
                    .content("""{"instanceName":"$instanceName","clientType":"$clientType"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.instanceId").exists())
                .andExpect(jsonPath("$.token").exists())
                .andReturn()
            
            val registerResponseBody = objectMapper.readTree(registerResponse.response.contentAsString)
            instanceId = registerResponseBody.get("instanceId").asText()
            token = registerResponseBody.get("token").asText()
        }

        test("step 3 - should verify instance exists in database") {
            val deviceRef = devicesRepository.get(UUID.fromString(instanceId))
            deviceRef shouldNotBe null
            val details = devicesRepository.fetchDetails(deviceRef!!)
            details.deviceId.toString() shouldBe instanceId
            details.deviceName shouldBe instanceName
            details.clientType shouldBe clientType
        }

        test("step 4 - should send client-info successfully") {
            val instanceBasic = Base64.getEncoder().encodeToString("$instanceId:$token".toByteArray())
            
            val clientInfoBody = """
                {
                    "version": "$clientVersion",
                    "timezoneOffsetSeconds": $timezoneOffsetSeconds,
                    "reportIntervalSeconds": $reportIntervalSeconds,
                    "availableStates": [
                        {
                            "deviceState": "ACTIVE",
                            "title": "Active"
                        }
                    ],
                    "knownApps": {
                        "com.example.app1": {
                            "appName": "App 1",
                            "iconBase64Png": null
                        },
                        "com.example.app2": {
                            "appName": "App 2",
                            "iconBase64Png": null
                        }
                    }
                }
            """.trimIndent()
            
            mockMvc.perform(
                post("/api/v2/client-info")
                    .header("Authorization", "Basic $instanceBasic")
                    .contentType("application/json")
                    .content(clientInfoBody)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("ok"))
        }

        test("step 5 - should verify client-info data in database") {
            val deviceRef = devicesRepository.get(UUID.fromString(instanceId))!!
            val details = devicesRepository.fetchDeviceDto(deviceRef)
            details.clientVersion shouldBe clientVersion
            details.clientTimezoneOffsetSeconds shouldBe timezoneOffsetSeconds
            details.reportIntervalSeconds?.toLong() shouldBe reportIntervalSeconds.toLong()
            details.knownApps shouldNotBe null
            devicesRepository.getAvailableDeviceStateTypes(deviceRef).size shouldBeGreaterThan 0
        }

        test("step 6 - should send first report successfully") {
            val instanceBasic = Base64.getEncoder().encodeToString("$instanceId:$token".toByteArray())
            
            val firstReportBody = """
                {
                    "screenTime": 600,
                    "applications": {
                        "com.example.app1": 400,
                        "com.example.app2": 200
                    }
                }
            """.trimIndent()
            
            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $instanceBasic")
                    .contentType("application/json")
                    .content(firstReportBody)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.deviceState").exists())
        }

        test("step 7 - should verify first report data in database") {
            val today = today()
            instanceRef = devicesRepository.get(UUID.fromString(instanceId))!!
            
            val firstScreenTime = devicesRepository.getScreenTimes(instanceRef, today)
            firstScreenTime.screenTimeSeconds shouldBe 600L
            firstScreenTime.applicationsSeconds["com.example.app1"] shouldBe 400L
            firstScreenTime.applicationsSeconds["com.example.app2"] shouldBe 200L
            firstScreenTime.updatedAt shouldNotBe null
            
            firstReportTimestamp = firstScreenTime.updatedAt
            
            // Small delay to ensure timestamp changes
            Thread.sleep(100)
        }

        test("step 8 - should send second report successfully") {
            val instanceBasic = Base64.getEncoder().encodeToString("$instanceId:$token".toByteArray())
            
            val secondReportBody = """
                {
                    "screenTime": 1200,
                    "applications": {
                        "com.example.app1": 800,
                        "com.example.app2": 400
                    }
                }
            """.trimIndent()
            
            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $instanceBasic")
                    .contentType("application/json")
                    .content(secondReportBody)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.deviceState").exists())
        }

        test("step 9 - should verify second report overwrote first report in database") {
            val today = today()
            
            val secondScreenTime = devicesRepository.getScreenTimes(instanceRef, today)
            secondScreenTime.screenTimeSeconds shouldBe 1200L
            secondScreenTime.applicationsSeconds["com.example.app1"] shouldBe 800L
            secondScreenTime.applicationsSeconds["com.example.app2"] shouldBe 400L
            secondScreenTime.updatedAt shouldNotBe null
            secondScreenTime.updatedAt shouldBeGreaterThan firstReportTimestamp
        }
    }
}

package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.cloud.firestore.Firestore
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
import pl.zarajczyk.familyrules.domain.DataRepository
import pl.zarajczyk.familyrules.domain.InstanceRef
import pl.zarajczyk.familyrules.domain.today
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class DeviceHappyPathIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var firestore: Firestore

    @Autowired
    private lateinit var dataRepository: DataRepository

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
            val userDoc = firestore.collection("users").document(username).get().get()
            userDoc shouldNotBe null
            userDoc.exists() shouldBe true
            userDoc.getString("username") shouldBe username
            userDoc.getString("passwordSha256") shouldNotBe null
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
            val instanceDoc = firestore.collection("users")
                .document(username)
                .collection("instances")
                .document(instanceId)
                .get().get()
            
            instanceDoc shouldNotBe null
            instanceDoc.exists() shouldBe true
            instanceDoc.getString("instanceId") shouldBe instanceId
            instanceDoc.getString("instanceName") shouldBe instanceName
            instanceDoc.getString("clientType") shouldBe clientType
            instanceDoc.getBoolean("deleted") shouldBe false
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
            val instanceDoc = firestore.collection("users")
                .document(username)
                .collection("instances")
                .document(instanceId)
                .get().get()
            
            instanceDoc.getString("clientVersion") shouldBe clientVersion
            instanceDoc.getLong("clientTimezoneOffsetSeconds") shouldBe timezoneOffsetSeconds.toLong()
            instanceDoc.getLong("reportIntervalSeconds") shouldBe reportIntervalSeconds.toLong()
            instanceDoc.getString("knownApps") shouldNotBe null
            instanceDoc.getString("deviceStates") shouldNotBe null
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
            instanceRef = dataRepository.findInstance(UUID.fromString(instanceId))!!
            
            val firstScreenTime = dataRepository.getScreenTimes(instanceRef, today)
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
            
            val secondScreenTime = dataRepository.getScreenTimes(instanceRef, today)
            secondScreenTime.screenTimeSeconds shouldBe 1200L
            secondScreenTime.applicationsSeconds["com.example.app1"] shouldBe 800L
            secondScreenTime.applicationsSeconds["com.example.app2"] shouldBe 400L
            secondScreenTime.updatedAt shouldNotBe null
            secondScreenTime.updatedAt shouldBeGreaterThan firstReportTimestamp
        }
    }
}

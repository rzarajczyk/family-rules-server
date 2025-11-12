package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
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
import pl.zarajczyk.familyrules.domain.DevicesRepository
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class DeviceErrorScenariosIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var devicesRepository: DevicesRepository

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
        val validUsername = "admin"
        val validPassword = "admin"
        val invalidUsername = "invalid-user"
        val invalidPassword = "wrong-password"
        val validInstanceName = "test-device-error-${System.currentTimeMillis()}"
        val clientType = "TEST"
        
        var validInstanceId: String = ""
        var validToken: String = ""

        context("/api/v2/register-instance error scenarios") {
            
            test("should return INVALID_PASSWORD when username is incorrect") {
                val basic = Base64.getEncoder().encodeToString("$invalidUsername:$validPassword".toByteArray())
                
                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType("application/json")
                        .content("""{"instanceName":"$validInstanceName","clientType":"$clientType"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("INVALID_PASSWORD"))
                    .andExpect(jsonPath("$.instanceId").doesNotExist())
                    .andExpect(jsonPath("$.token").doesNotExist())
            }

            test("should return INVALID_PASSWORD when password is incorrect") {
                val basic = Base64.getEncoder().encodeToString("$validUsername:$invalidPassword".toByteArray())
                
                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType("application/json")
                        .content("""{"instanceName":"$validInstanceName","clientType":"$clientType"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("INVALID_PASSWORD"))
                    .andExpect(jsonPath("$.instanceId").doesNotExist())
                    .andExpect(jsonPath("$.token").doesNotExist())
            }

            test("should return ILLEGAL_INSTANCE_NAME when instance name is empty") {
                val basic = Base64.getEncoder().encodeToString("$validUsername:$validPassword".toByteArray())
                
                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType("application/json")
                        .content("""{"instanceName":"","clientType":"$clientType"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("ILLEGAL_INSTANCE_NAME"))
                    .andExpect(jsonPath("$.instanceId").doesNotExist())
                    .andExpect(jsonPath("$.token").doesNotExist())
            }

            test("should register a valid instance for duplicate test") {
                val basic = Base64.getEncoder().encodeToString("$validUsername:$validPassword".toByteArray())
                
                val registerResponse = mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType("application/json")
                        .content("""{"instanceName":"$validInstanceName","clientType":"$clientType"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.instanceId").exists())
                    .andExpect(jsonPath("$.token").exists())
                    .andReturn()
                
                val registerResponseBody = objectMapper.readTree(registerResponse.response.contentAsString)
                validInstanceId = registerResponseBody.get("instanceId").asText()
                validToken = registerResponseBody.get("token").asText()
            }

            test("should return INSTANCE_ALREADY_EXISTS when instance name already exists") {
                val basic = Base64.getEncoder().encodeToString("$validUsername:$validPassword".toByteArray())
                
                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType("application/json")
                        .content("""{"instanceName":"$validInstanceName","clientType":"$clientType"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("INSTANCE_ALREADY_EXISTS"))
                    .andExpect(jsonPath("$.instanceId").doesNotExist())
                    .andExpect(jsonPath("$.token").doesNotExist())
            }

            test("should return 4xx when request JSON is malformed") {
                val basic = Base64.getEncoder().encodeToString("$validUsername:$validPassword".toByteArray())
                
                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType("application/json")
                        .content("""{"invalidField":"value"}""")
                )
                    .andExpect(status().is4xxClientError)
            }

            test("should return 4xx when request body is not valid JSON") {
                val basic = Base64.getEncoder().encodeToString("$validUsername:$validPassword".toByteArray())
                
                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType("application/json")
                        .content("""not a valid json""")
                )
                    .andExpect(status().is4xxClientError)
            }
        }

        context("/api/v2/client-info error scenarios") {
            
            test("should return 401 when instanceId is incorrect") {
                val invalidInstanceId = "00000000-0000-0000-0000-000000000000"
                val instanceBasic = Base64.getEncoder().encodeToString("$invalidInstanceId:$validToken".toByteArray())
                
                val clientInfoBody = """
                    {
                        "version": "v1.0.0",
                        "timezoneOffsetSeconds": 3600,
                        "reportIntervalSeconds": 60,
                        "availableStates": [
                            {
                                "deviceState": "ACTIVE",
                                "title": "Active"
                            }
                        ],
                        "knownApps": {}
                    }
                """.trimIndent()
                
                mockMvc.perform(
                    post("/api/v2/client-info")
                        .header("Authorization", "Basic $instanceBasic")
                        .contentType("application/json")
                        .content(clientInfoBody)
                )
                    .andExpect(status().isUnauthorized)
            }

            test("should return 401 when token is incorrect") {
                val invalidToken = "invalid-token-123"
                val instanceBasic = Base64.getEncoder().encodeToString("$validInstanceId:$invalidToken".toByteArray())
                
                val clientInfoBody = """
                    {
                        "version": "v1.0.0",
                        "timezoneOffsetSeconds": 3600,
                        "reportIntervalSeconds": 60,
                        "availableStates": [
                            {
                                "deviceState": "ACTIVE",
                                "title": "Active"
                            }
                        ],
                        "knownApps": {}
                    }
                """.trimIndent()
                
                mockMvc.perform(
                    post("/api/v2/client-info")
                        .header("Authorization", "Basic $instanceBasic")
                        .contentType("application/json")
                        .content(clientInfoBody)
                )
                    .andExpect(status().isUnauthorized)
            }

            test("should return 4xx when request JSON is malformed") {
                val instanceBasic = Base64.getEncoder().encodeToString("$validInstanceId:$validToken".toByteArray())
                
                mockMvc.perform(
                    post("/api/v2/client-info")
                        .header("Authorization", "Basic $instanceBasic")
                        .contentType("application/json")
                        .content("""{"invalidField":"value"}""")
                )
                    .andExpect(status().is4xxClientError)
            }

            test("should return 4xx when request body is not valid JSON") {
                val instanceBasic = Base64.getEncoder().encodeToString("$validInstanceId:$validToken".toByteArray())
                
                mockMvc.perform(
                    post("/api/v2/client-info")
                        .header("Authorization", "Basic $instanceBasic")
                        .contentType("application/json")
                        .content("""not a valid json""")
                )
                    .andExpect(status().is4xxClientError)
            }
        }

        context("/api/v2/report error scenarios") {
            
            test("should return 401 when instanceId is incorrect") {
                val invalidInstanceId = "00000000-0000-0000-0000-000000000000"
                val instanceBasic = Base64.getEncoder().encodeToString("$invalidInstanceId:$validToken".toByteArray())
                
                val reportBody = """
                    {
                        "screenTime": 600,
                        "applications": {
                            "com.example.app1": 400
                        }
                    }
                """.trimIndent()
                
                mockMvc.perform(
                    post("/api/v2/report")
                        .header("Authorization", "Basic $instanceBasic")
                        .contentType("application/json")
                        .content(reportBody)
                )
                    .andExpect(status().isUnauthorized)
            }

            test("should return 401 when token is incorrect") {
                val invalidToken = "invalid-token-456"
                val instanceBasic = Base64.getEncoder().encodeToString("$validInstanceId:$invalidToken".toByteArray())
                
                val reportBody = """
                    {
                        "screenTime": 600,
                        "applications": {
                            "com.example.app1": 400
                        }
                    }
                """.trimIndent()
                
                mockMvc.perform(
                    post("/api/v2/report")
                        .header("Authorization", "Basic $instanceBasic")
                        .contentType("application/json")
                        .content(reportBody)
                )
                    .andExpect(status().isUnauthorized)
            }

            test("should return 4xx when request JSON is malformed") {
                val instanceBasic = Base64.getEncoder().encodeToString("$validInstanceId:$validToken".toByteArray())
                
                mockMvc.perform(
                    post("/api/v2/report")
                        .header("Authorization", "Basic $instanceBasic")
                        .contentType("application/json")
                        .content("""{"invalidField":"value"}""")
                )
                    .andExpect(status().is4xxClientError)
            }

            test("should return 4xx when request body is not valid JSON") {
                val instanceBasic = Base64.getEncoder().encodeToString("$validInstanceId:$validToken".toByteArray())
                
                mockMvc.perform(
                    post("/api/v2/report")
                        .header("Authorization", "Basic $instanceBasic")
                        .contentType("application/json")
                        .content("""not a valid json""")
                )
                    .andExpect(status().is4xxClientError)
            }
        }
    }
}

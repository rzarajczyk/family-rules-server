package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class V2InitialSetupControllerIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var usersRepository: UsersRepository

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
        val username = "setup-user-${System.currentTimeMillis()}"
        val password = "setup-pass"

        beforeSpec {
            usersRepository.createUser(username, password.sha256(), AccessLevel.PARENT)
        }

        afterSpec {
            usersRepository.get(username)?.also { ref ->
                // delete all devices for cleanup
                devicesRepository.getAll(username).forEach { devicesRepository.delete(it) }
                usersRepository.delete(ref)
            }
        }

        context("POST /api/v2/register-instance") {
            test("should register instance successfully and persist device") {
                val basic = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                val body = """{"instanceName":"Test Device","clientType":"TEST"}"""

                val result = mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.instanceId").exists())
                    .andExpect(jsonPath("$.token").exists())
                    .andReturn()

                val json = objectMapper.readTree(result.response.contentAsString)
                val instanceId = json.get("instanceId").asText()
                instanceId.shouldNotBeBlank()

                // Verify repository side-effects
                val deviceRef = devicesRepository.get(java.util.UUID.fromString(instanceId))
                deviceRef shouldNotBe null
                val details = devicesRepository.fetchDetails(deviceRef!!)
                details.deviceName shouldBe "Test Device"
                details.clientType shouldBe "TEST"
            }

            test("should reject when password is invalid") {
                val basic = Base64.getEncoder().encodeToString("$username:wrong".toByteArray())
                val body = """{"instanceName":"Some Name","clientType":"TEST"}"""

                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("INVALID_PASSWORD"))
            }

            test("should return INSTANCE_ALREADY_EXISTS when name duplicated for user") {
                val basic = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                val body = """{"instanceName":"Duplicate Name","clientType":"TEST"}"""

                // First OK
                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("SUCCESS"))

                // Second duplicate
                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("INSTANCE_ALREADY_EXISTS"))
            }

            test("should return ILLEGAL_INSTANCE_NAME when name too short") {
                val basic = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                val body = """{"instanceName":"ab","clientType":"TEST"}""" // < 3 chars

                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("ILLEGAL_INSTANCE_NAME"))
            }

            test("should fail with 400 on invalid JSON body") {
                val basic = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                val invalidJson = "{" // malformed

                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
            }

            test("should fail with 500 when Authorization header missing or malformed") {
                // Controller's decodeBasicAuth throws RuntimeException when header is missing/malformed
                val body = """{"instanceName":"No Auth","clientType":"TEST"}"""

                mockMvc.perform(
                    post("/api/v2/register-instance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isInternalServerError)
            }
        }

        context("POST /api/v2/unregister-instance") {
            test("should return SUCCESS regardless of input (placeholder endpoint)") {
                // First, register an instance to obtain instanceId and token (register is permitAll)
                val username = "setup-user-${System.currentTimeMillis()}"
                val password = "setup-pass"
                usersRepository.createUser(username, password.sha256(), AccessLevel.PARENT)
                val registerBasic = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                val registerBody = """{"instanceName":"Tmp Device","clientType":"TEST"}"""

                val registerResult = mockMvc.perform(
                    post("/api/v2/register-instance")
                        .header("Authorization", "Basic $registerBasic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.instanceId").exists())
                    .andExpect(jsonPath("$.token").exists())
                    .andReturn()

                val regJson = objectMapper.readTree(registerResult.response.contentAsString)
                val instanceId = regJson.get("instanceId").asText()
                val token = regJson.get("token").asText()

                // Now call unregister with v2 API auth (instanceId:token)
                val apiV2Basic = Base64.getEncoder().encodeToString("$instanceId:$token".toByteArray())

                val result = mockMvc.perform(
                    post("/api/v2/unregister-instance")
                        .header("Authorization", "Basic $apiV2Basic")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andReturn()

                val json = objectMapper.readTree(result.response.contentAsString)
                json.get("status").asText() shouldBe "SUCCESS"
            }
        }
    }
}

package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
class V2ClientInfoControllerIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var usersRepository: UsersRepository

    @Autowired
    private lateinit var devicesRepository: DevicesRepository

    @Autowired
    private lateinit var devicesService: DevicesService

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
        val username = "clientinfo-user-" + System.currentTimeMillis()
        val password = "pass"
        val instanceName = "ClientInfo Device"
        val clientType = "TEST"

        lateinit var deviceId: DeviceId
        lateinit var token: String

        beforeSpec {
            usersRepository.createUser(username, password.sha256(), AccessLevel.PARENT)
            val newDevice = devicesService.setupNewDevice(username, instanceName, clientType)
            deviceId = newDevice.deviceId
            token = newDevice.token
        }

        afterSpec {
            devicesRepository.get(deviceId)?.also { devicesRepository.delete(it) }
            usersRepository.get(username)?.also { usersRepository.delete(it) }
        }

        context("POST /api/v2/client-info - happy path") {
            test("should update client info and persist known apps and states") {
                val basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
                val body = """
                    {
                      "version": "v9.9.9",
                      "timezoneOffsetSeconds": 7200,
                      "reportIntervalSeconds": 30,
                      "availableStates": [
                        {
                          "deviceState": "ACTIVE",
                          "title": "Active",
                          "icon": null,
                          "description": null,
                          "arguments": ["APP_GROUP", "INVALID_ARG"]
                        }
                      ],
                      "knownApps": {
                        "com.example.app1": { "appName": "App One", "iconBase64Png": null },
                        "com.example.app2": { "appName": "App Two", "iconBase64Png": null }
                      }
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/v2/client-info")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("ok"))

                val deviceRef = devicesRepository.get(UUID.fromString(deviceId.toString()))!!.also { it.shouldNotBeNull() }
                val dto = devicesRepository.fetchDetails(deviceRef)

                dto.clientVersion shouldBe "v9.9.9"
                dto.clientTimezoneOffsetSeconds shouldBe 7200
                dto.reportIntervalSeconds shouldBe 30
                dto.knownApps.keys.shouldContainAll(listOf("com.example.app1", "com.example.app2"))
                dto.knownApps["com.example.app1"]!!.appName shouldBe "App One"

                val states = dto.availableDeviceStates
                states shouldNotBe null
                states shouldHaveSize 1
                states[0].deviceState shouldBe "ACTIVE"
                states[0].title shouldBe "Active"
                // Should only keep valid enum arguments
                states[0].arguments.map { it.name } shouldContain "APP_GROUP"
            }
        }

        context("POST /api/v2/launch - alias mapping") {
            test("should behave the same as /client-info") {
                val basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
                val body = """
                    {
                      "version": "v2.0.0",
                      "timezoneOffsetSeconds": 0,
                      "reportIntervalSeconds": 120,
                      "availableStates": [
                        { "deviceState": "ACTIVE", "title": "Active", "icon": null, "description": null, "arguments": [] }
                      ],
                      "knownApps": {}
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/v2/launch")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("ok"))
            }
        }

        context("POST /api/v2/client-info - defaults and nulls") {
            test("should apply defaults when nulls or missing fields provided") {
                val basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
                val body = """
                    {
                      "version": "v1.2.3",
                      "timezoneOffsetSeconds": null,
                      "reportIntervalSeconds": null,
                      "availableStates": [
                        { "deviceState": "ACTIVE", "title": "Active", "icon": null, "description": null, "arguments": null }
                      ]
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/v2/client-info")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("ok"))

                val deviceRef = devicesRepository.get(deviceId)!!
                val dto = devicesRepository.fetchDetails(deviceRef)
                dto.clientVersion shouldBe "v1.2.3"
                dto.clientTimezoneOffsetSeconds shouldBe 0
                dto.reportIntervalSeconds shouldBe 60
                dto.knownApps.keys shouldHaveSize 0
            }
        }

        context("POST /api/v2/client-info - invalid JSON") {
            test("should return 400 with BAD_REQUEST error") {
                val basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
                val invalid = "{" // malformed JSON

                mockMvc.perform(
                    post("/api/v2/client-info")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
            }
        }
    }
}

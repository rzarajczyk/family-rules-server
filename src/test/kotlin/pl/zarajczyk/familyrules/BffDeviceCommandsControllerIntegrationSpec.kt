package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.containsString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.gcloud.FirestoreEmulatorContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import pl.zarajczyk.familyrules.domain.CommandResultStatus
import pl.zarajczyk.familyrules.domain.DeviceCommandsService
import pl.zarajczyk.familyrules.domain.DevicesService
import pl.zarajczyk.familyrules.domain.sha256
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class BffDeviceCommandsControllerIntegrationSpec : FunSpec() {

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

    @Autowired
    private lateinit var deviceCommandsService: DeviceCommandsService

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
        val username = "bff-device-commands-user-${System.currentTimeMillis()}"
        val otherUsername = "bff-device-commands-other-${System.currentTimeMillis()}"
        lateinit var deviceId: java.util.UUID

        beforeSpec {
            usersRepository.createUser(username, "pass".sha256(), pl.zarajczyk.familyrules.domain.AccessLevel.PARENT)
            usersRepository.createUser(otherUsername, "pass".sha256(), pl.zarajczyk.familyrules.domain.AccessLevel.PARENT)
        }

        beforeTest {
            val device = devicesService.setupNewDevice(username, "BFF Command Device-${System.nanoTime()}", "TEST")
            deviceId = device.deviceId
        }

        afterTest {
            devicesRepository.get(deviceId)?.also { devicesRepository.delete(it) }
        }

        afterSpec {
            usersRepository.get(username)?.also { usersRepository.delete(it) }
            usersRepository.get(otherUsername)?.also { usersRepository.delete(it) }
        }

        test("should reject unsupported command creation") {
            mockMvc.perform(
                post("/bff/instance-commands")
                    .param("instanceId", deviceId.toString())
                    .with(user(username))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "commandName": "SEND_LOGS" }""")
            )
                .andExpect(status().isUnprocessableEntity)
        }

        test("should enqueue command and return completed result") {
            val device = devicesService.get(deviceId)
            device.update(DeviceDetailsUpdateDto(supportedServerCommands = set(listOf("SEND_LOGS"))))

            val createResult = mockMvc.perform(
                post("/bff/instance-commands")
                    .param("instanceId", deviceId.toString())
                    .with(user(username))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "commandName": "SEND_LOGS" }""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.commandId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()

            val commandId = objectMapper.readTree(createResult.response.contentAsString).get("commandId").asText()
            deviceCommandsService.submitResults(device, listOf(
                pl.zarajczyk.familyrules.domain.port.CommandResultDto(
                    commandId = commandId,
                    commandName = "SEND_LOGS",
                    completedAt = kotlinx.datetime.Instant.parse("2026-05-03T12:01:05Z"),
                    status = CommandResultStatus.SUCCEEDED,
                    responseType = "SEND_LOGS_V1",
                    responsePayloadJson = "{\"logsText\":\"hello\"}",
                )
            ))

            mockMvc.perform(
                get("/bff/instance-commands")
                    .param("instanceId", deviceId.toString())
                    .param("commandName", "SEND_LOGS")
                    .with(user(username))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.responseType").value("SEND_LOGS_V1"))
                .andExpect(jsonPath("$.responsePayload.logsText").value("hello"))
        }

        test("should return empty when no request exists and support clear") {
            val device = devicesService.get(deviceId)
            device.update(DeviceDetailsUpdateDto(supportedServerCommands = set(listOf("SEND_LOGS"))))

            mockMvc.perform(
                get("/bff/instance-commands")
                    .param("instanceId", deviceId.toString())
                    .param("commandName", "SEND_LOGS")
                    .with(user(username))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("EMPTY"))

            mockMvc.perform(
                post("/bff/instance-commands")
                    .param("instanceId", deviceId.toString())
                    .with(user(username))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "commandName": "SEND_LOGS" }""")
            )
                .andExpect(status().isOk)

            mockMvc.perform(
                delete("/bff/instance-commands")
                    .param("instanceId", deviceId.toString())
                    .param("commandName", "SEND_LOGS")
                    .with(user(username))
            )
                .andExpect(status().isOk)

            mockMvc.perform(
                get("/bff/instance-commands")
                    .param("instanceId", deviceId.toString())
                    .param("commandName", "SEND_LOGS")
                    .with(user(username))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("EMPTY"))
        }

        test("should forbid access from another user") {
            val device = devicesService.get(deviceId)
            device.update(DeviceDetailsUpdateDto(supportedServerCommands = set(listOf("SEND_LOGS"))))

            mockMvc.perform(
                post("/bff/instance-commands")
                    .param("instanceId", deviceId.toString())
                    .with(user(otherUsername))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "commandName": "SEND_LOGS" }""")
            )
                .andExpect(status().isForbidden)
        }

        test("should redirect unauthenticated requests to login") {
            mockMvc.perform(
                post("/bff/instance-commands")
                    .param("instanceId", deviceId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "commandName": "SEND_LOGS" }""")
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(header().string("Location", containsString("/gui/login.html")))
        }
    }
}

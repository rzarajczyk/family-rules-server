package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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
import pl.zarajczyk.familyrules.domain.CommandResultStatus
import pl.zarajczyk.familyrules.domain.DeviceCommandsService
import pl.zarajczyk.familyrules.domain.DevicesService
import pl.zarajczyk.familyrules.domain.sha256
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set
import java.util.Base64

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class V2DeviceCommandsControllerIntegrationSpec : FunSpec() {

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
        val username = "device-commands-user-${System.currentTimeMillis()}"
        val password = "pass"
        lateinit var deviceId: java.util.UUID
        lateinit var token: String

        beforeSpec {
            usersRepository.createUser(username, password.sha256(), pl.zarajczyk.familyrules.domain.AccessLevel.PARENT)
        }

        beforeTest {
            val newDevice = devicesService.setupNewDevice(username, "Command Device-${System.nanoTime()}", "TEST")
            deviceId = newDevice.deviceId
            token = newDevice.token
            val device = devicesService.get(deviceId)
            device.update(DeviceDetailsUpdateDto(supportedServerCommands = set(listOf("SEND_LOGS"))))
        }

        afterTest {
            devicesRepository.get(deviceId)?.also { devicesRepository.delete(it) }
        }

        afterSpec {
            usersRepository.get(username)?.also { usersRepository.delete(it) }
        }

        test("should acknowledge commands idempotently") {
            val basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            val command = deviceCommandsService.enqueue(devicesService.get(deviceId), "SEND_LOGS")
            val body = """
                {
                  "acks": [
                    {
                      "commandId": "${command.commandId}",
                      "receivedAt": "2026-05-03T12:00:20Z"
                    }
                  ]
                }
            """.trimIndent()

            repeat(2) {
                mockMvc.perform(
                    post("/api/v2/command-acks")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("ok"))
            }

            val stored = deviceCommandsService.getForDevice(devicesService.get(deviceId), command.commandId)
            stored.status.name shouldBe "ACKNOWLEDGED"
        }

        test("should accept result before ack and mark command completed") {
            val basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            val command = deviceCommandsService.enqueue(devicesService.get(deviceId), "SEND_LOGS")
            val body = """
                {
                  "results": [
                    {
                      "commandId": "${command.commandId}",
                      "commandName": "SEND_LOGS",
                      "completedAt": "2026-05-03T12:01:05Z",
                      "status": "SUCCEEDED",
                      "responseType": "SEND_LOGS_V1",
                      "responsePayload": {
                        "logsText": "hello",
                        "truncated": false
                      }
                    }
                  ]
                }
            """.trimIndent()

            repeat(2) {
                mockMvc.perform(
                    post("/api/v2/command-results")
                        .header("Authorization", "Basic $basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                    .andExpect(status().isOk)
            }

            val stored = deviceCommandsService.getForDevice(devicesService.get(deviceId), command.commandId)
            stored.status.name shouldBe "COMPLETED"
            stored.resultStatus shouldBe CommandResultStatus.SUCCEEDED
            val payload = objectMapper.readTree(stored.responsePayloadJson)
            payload.get("days").size() shouldBe 1
            payload.get("days")[0].get("day").asText() shouldBe "day-1"
        }
    }
}

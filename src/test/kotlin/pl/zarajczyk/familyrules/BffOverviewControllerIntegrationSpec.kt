package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.gcloud.FirestoreEmulatorContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class BffOverviewControllerIntegrationSpec : FunSpec() {

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
        val testUsername = "overview-user-${System.currentTimeMillis()}"
        val testPassword = "overview-pass"
        var testInstanceId: InstanceId? = null

        beforeSpec {
            usersRepository.createUser(testUsername, testPassword.sha256(), AccessLevel.PARENT)
        }

        afterSpec {
            usersRepository.get(testUsername)?.also { ref ->
                devicesRepository.getAll(testUsername).forEach { devicesRepository.delete(it) }
                usersRepository.delete(ref)
            }
        }

        context("GET /bff/status") {
            test("should return empty list when user has no instances") {
                val result = mockMvc.perform(
                    get("/bff/status")
                        .param("date", "2024-01-15")
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.instances").isArray)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val instances = response.get("instances")
                instances.isArray shouldBe true
                instances.size() shouldBe 0
            }

            test("should return instances with screen time data for authenticated user") {
                // Create a test device
                val deviceName = "Test Device Status"
                val deviceDetails = devicesService.setupNewDevice(testUsername, deviceName, "TEST")
                testInstanceId = deviceDetails.deviceId

                val result = mockMvc.perform(
                    get("/bff/status")
                        .param("date", "2024-01-15")
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.instances").isArray)
                    .andExpect(jsonPath("$.instances[0].instanceId").exists())
                    .andExpect(jsonPath("$.instances[0].instanceName").value(deviceName))
                    .andExpect(jsonPath("$.instances[0].screenTimeSeconds").exists())
                    .andExpect(jsonPath("$.instances[0].appUsageSeconds").isArray)
                    .andExpect(jsonPath("$.instances[0].automaticDeviceState").exists())
                    .andExpect(jsonPath("$.instances[0].online").exists())
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val instances = response.get("instances")
                instances.size() shouldBe 1
                instances[0].get("instanceName").asText() shouldBe deviceName
            }

            test("should redirect to login page for unauthenticated request") {
                mockMvc.perform(
                    get("/bff/status")
                        .param("date", "2024-01-15")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }

            test("should require date parameter") {
                mockMvc.perform(
                    get("/bff/status")
                        .with(user(testUsername))
                )
                    .andExpect(status().is5xxServerError)
            }
        }

        context("GET /bff/instance-info") {
            test("should return instance information for valid instanceId") {
                val deviceName = "Test Device Info"
                val deviceDetails = devicesService.setupNewDevice(testUsername, deviceName, "ANDROID")
                val instanceId = deviceDetails.deviceId.toString()

                val result = mockMvc.perform(
                    get("/bff/instance-info")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.instanceId").value(instanceId))
                    .andExpect(jsonPath("$.instanceName").value(deviceName))
                    .andExpect(jsonPath("$.clientType").value("ANDROID"))
                    .andExpect(jsonPath("$.clientVersion").exists())
                    .andExpect(jsonPath("$.clientTimezoneOffsetSeconds").exists())
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("instanceId").asText() shouldBe instanceId
                response.get("instanceName").asText() shouldBe deviceName
            }

            test("should return 500 when instanceId does not exist") {
                val nonExistentId = UUID.randomUUID().toString()

                mockMvc.perform(
                    get("/bff/instance-info")
                        .param("instanceId", nonExistentId)
                        .with(user(testUsername))
                )
                    .andExpect(status().is5xxServerError)
            }

            test("should redirect to login page for unauthenticated request") {
                mockMvc.perform(
                    get("/bff/instance-info")
                        .param("instanceId", UUID.randomUUID().toString())
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("GET /bff/instance-edit-info") {
            test("should return instance edit information") {
                val deviceName = "Test Device Edit Info"
                val deviceDetails = devicesService.setupNewDevice(testUsername, deviceName, "TEST")
                val instanceId = deviceDetails.deviceId.toString()

                val result = mockMvc.perform(
                    get("/bff/instance-edit-info")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.instanceName").value(deviceName))
                    .andExpect(jsonPath("$.icon").exists())
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("instanceName").asText() shouldBe deviceName
            }

            test("should return 500 when instanceId does not exist") {
                mockMvc.perform(
                    get("/bff/instance-edit-info")
                        .param("instanceId", UUID.randomUUID().toString())
                        .with(user(testUsername))
                )
                    .andExpect(status().is5xxServerError)
            }
        }

        context("POST /bff/instance-edit-info") {
            test("should update instance name and icon successfully") {
                val deviceName = "Original Device Name"
                val deviceDetails = devicesService.setupNewDevice(testUsername, deviceName, "TEST")
                val deviceRef = devicesRepository.get(deviceDetails.deviceId)!!
                val instanceId = deviceDetails.deviceId.toString()

                val updateRequest = """
                    {
                        "instanceName": "Updated Device Name",
                        "icon": {
                            "type": "image/png",
                            "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                        }
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/instance-edit-info")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest)
                )
                    .andExpect(status().isOk)

                // Verify the update
                val updatedRef = devicesRepository.get(deviceDetails.deviceId)!!
                val details = devicesRepository.fetchDetails(updatedRef)
                details.deviceName shouldBe "Updated Device Name"
                details.iconType shouldBe "image/png"
                details.iconData.shouldNotBeNull()
            }

            test("should update instance name with null icon") {
                val deviceName = "Test Device Null Icon"
                val deviceDetails = devicesService.setupNewDevice(testUsername, deviceName, "TEST")
                val deviceRef = devicesRepository.get(deviceDetails.deviceId)!!
                val instanceId = deviceDetails.deviceId.toString()

                val updateRequest = """
                    {
                        "instanceName": "Updated Name Only",
                        "icon": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/instance-edit-info")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest)
                )
                    .andExpect(status().isOk)

                val updatedRef = devicesRepository.get(deviceDetails.deviceId)!!
                val details = devicesRepository.fetchDetails(updatedRef)
                details.deviceName shouldBe "Updated Name Only"
            }

            test("should return 500 when instanceId does not exist") {
                val updateRequest = """
                    {
                        "instanceName": "New Name",
                        "icon": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/instance-edit-info")
                        .param("instanceId", UUID.randomUUID().toString())
                        .with(user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest)
                )
                    .andExpect(status().is5xxServerError)
            }
        }

        context("GET /bff/instance-state") {
            test("should return instance state with available states") {
                val deviceName = "Test Device State"
                val deviceDetails = devicesService.setupNewDevice(testUsername, deviceName, "TEST")
                val instanceId = deviceDetails.deviceId.toString()

                val result = mockMvc.perform(
                    get("/bff/instance-state")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.instanceId").exists())
                    .andExpect(jsonPath("$.instanceName").value(deviceName))
                    .andExpect(jsonPath("$.availableStates").isArray)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("instanceId").asText() shouldBe instanceId
                response.get("availableStates").isArray shouldBe true
            }

            test("should return 500 when instanceId does not exist") {
                mockMvc.perform(
                    get("/bff/instance-state")
                        .param("instanceId", UUID.randomUUID().toString())
                        .with(user(testUsername))
                )
                    .andExpect(status().is5xxServerError)
            }

            test("should redirect to login page for unauthenticated request") {
                mockMvc.perform(
                    get("/bff/instance-state")
                        .param("instanceId", UUID.randomUUID().toString())
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("POST /bff/instance-state") {
            test("should set forced device state successfully") {
                val deviceDetails = devicesService.setupNewDevice(testUsername, "Test Device Set State", "TEST")
                val deviceRef = devicesRepository.get(deviceDetails.deviceId)!!
                val instanceId = deviceDetails.deviceId.toString()

                val stateRequest = """
                    {
                        "forcedDeviceState": "LOCKED",
                        "extra": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/instance-state")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest)
                )
                    .andExpect(status().isOk)

                // Verify the state was set
                val updatedRef = devicesRepository.get(deviceDetails.deviceId)!!
                val details = devicesRepository.fetchDetails(updatedRef)
                details.forcedDeviceState.shouldNotBeNull()
                details.forcedDeviceState?.deviceState shouldBe "LOCKED"
            }

            test("should clear forced device state when empty string provided") {
                val deviceDetails = devicesService.setupNewDevice(testUsername, "Test Device Clear State", "TEST")
                val deviceRef = devicesRepository.get(deviceDetails.deviceId)!!
                val instanceId = deviceDetails.deviceId.toString()

                // First set a state
                devicesRepository.setForcedInstanceState(
                    deviceRef,
                    DeviceStateDto(deviceState = "LOCKED", extra = null)
                )

                // Then clear it
                val clearStateRequest = """
                    {
                        "forcedDeviceState": "",
                        "extra": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/instance-state")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearStateRequest)
                )
                    .andExpect(status().isOk)

                val updatedRef = devicesRepository.get(deviceDetails.deviceId)!!
                val details = devicesRepository.fetchDetails(updatedRef)
                details.forcedDeviceState.shouldBeNull()
            }

            test("should set forced device state with extra parameter") {
                val deviceDetails = devicesService.setupNewDevice(testUsername, "Test Device State Extra", "TEST")
                val deviceRef = devicesRepository.get(deviceDetails.deviceId)!!
                val instanceId = deviceDetails.deviceId.toString()

                val stateRequest = """
                    {
                        "forcedDeviceState": "LIMITED_TO",
                        "extra": "some-group-id"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/instance-state")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest)
                )
                    .andExpect(status().isOk)

                val updatedRef = devicesRepository.get(deviceDetails.deviceId)!!
                val details = devicesRepository.fetchDetails(updatedRef)
                details.forcedDeviceState.shouldNotBeNull()
                details.forcedDeviceState?.deviceState shouldBe "LIMITED_TO"
                details.forcedDeviceState?.extra shouldBe "some-group-id"
            }

            test("should return 500 when instanceId does not exist") {
                val stateRequest = """
                    {
                        "forcedDeviceState": "LOCKED",
                        "extra": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/instance-state")
                        .param("instanceId", UUID.randomUUID().toString())
                        .with(user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest)
                )
                    .andExpect(status().is5xxServerError)
            }
        }

        context("POST /bff/delete-instance") {
            test("should delete instance successfully") {
                val deviceDetails = devicesService.setupNewDevice(testUsername, "Test Device To Delete", "TEST")
                val instanceId = deviceDetails.deviceId.toString()

                mockMvc.perform(
                    post("/bff/delete-instance")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)

                // Verify the instance was deleted
                val deletedRef = devicesRepository.get(UUID.fromString(instanceId))
                deletedRef.shouldBeNull()
            }

            test("should return 500 when trying to delete non-existent instance") {
                mockMvc.perform(
                    post("/bff/delete-instance")
                        .param("instanceId", UUID.randomUUID().toString())
                        .with(user(testUsername))
                )
                    .andExpect(status().is5xxServerError)
            }

            test("should redirect to login page for unauthenticated request") {
                mockMvc.perform(
                    post("/bff/delete-instance")
                        .param("instanceId", UUID.randomUUID().toString())
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("Integration scenarios") {
            test("should handle complete instance lifecycle") {
                // Create instance
                val deviceDetails = devicesService.setupNewDevice(testUsername, "Lifecycle Test Device", "TEST")
                val instanceId = deviceDetails.deviceId.toString()

                // Get instance info
                mockMvc.perform(
                    get("/bff/instance-info")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.instanceName").value("Lifecycle Test Device"))

                // Update instance
                val updateRequest = """{"instanceName": "Updated Lifecycle Device", "icon": null}"""
                mockMvc.perform(
                    post("/bff/instance-edit-info")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest)
                )
                    .andExpect(status().isOk)

                // Verify update
                mockMvc.perform(
                    get("/bff/instance-edit-info")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.instanceName").value("Updated Lifecycle Device"))

                // Set state
                val stateRequest = """{"forcedDeviceState": "LOCKED", "extra": null}"""
                mockMvc.perform(
                    post("/bff/instance-state")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stateRequest)
                )
                    .andExpect(status().isOk)

                // Verify state
                mockMvc.perform(
                    get("/bff/instance-state")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)

                // Check status endpoint
                mockMvc.perform(
                    get("/bff/status")
                        .param("date", "2024-01-15")
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.instances[?(@.instanceId=='$instanceId')].instanceName").value("Updated Lifecycle Device"))

                // Delete instance
                mockMvc.perform(
                    post("/bff/delete-instance")
                        .param("instanceId", instanceId)
                        .with(user(testUsername))
                )
                    .andExpect(status().isOk)

                // Verify deletion
                val allInstances = devicesRepository.getAll(testUsername)
                allInstances.none { devicesRepository.fetchDetails(it).deviceId.toString() == instanceId } shouldBe true
            }
        }
    }
}

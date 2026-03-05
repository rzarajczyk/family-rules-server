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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.gcloud.FirestoreEmulatorContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class IntegrationAppGroupsControllerIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var usersRepository: UsersRepository
    @Autowired private lateinit var usersService: UsersService
    @Autowired private lateinit var devicesService: DevicesService
    @Autowired private lateinit var devicesRepository: DevicesRepository
    @Autowired private lateinit var appGroupService: AppGroupService
    @Autowired private lateinit var groupStateService: GroupStateService

    companion object {
        @Container @JvmStatic
        val firestoreContainer: FirestoreEmulatorContainer =
            FirestoreEmulatorContainer("gcr.io/google.com/cloudsdktool/google-cloud-cli:546.0.0-emulators")

        @JvmStatic @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            firestoreContainer.start()
            registry.add("firestore.emulator-host") { firestoreContainer.emulatorEndpoint }
        }
    }

    init {
        val username = "integration-api-user-${System.currentTimeMillis()}"
        lateinit var token: String
        lateinit var groupId: String
        lateinit var lockedStateId: String
        lateinit var allowedStateId: String
        lateinit var deviceId: DeviceId

        beforeSpec {
            // Create user
            usersService.createUser(username, "testpass", AccessLevel.ADMIN)
            val user = usersService.get(username)

            // Generate integration API token
            val integrationToken = UUID.randomUUID().toString()
            user.updateIntegrationApiToken(integrationToken)
            token = integrationToken

            // Register a device
            val device = devicesService.setupNewDevice(username, "Integration Test Device", "TEST")
            deviceId = device.deviceId

            // Create an app group
            val group = appGroupService.createAppGroup(user, "Games")
            groupId = group.fetchDetails().id

            // Create two named states referencing the device
            val locked = groupStateService.createGroupState(
                group, "Locked",
                mapOf(deviceId to DeviceStateDto(deviceState = "LOCKED", extra = null))
            )
            lockedStateId = locked.fetchDetails().id

            val allowed = groupStateService.createGroupState(
                group, "Allowed",
                mapOf(deviceId to DeviceStateDto(deviceState = "ACTIVE", extra = null))
            )
            allowedStateId = allowed.fetchDetails().id
        }

        afterSpec {
            devicesRepository.get(deviceId)?.also { devicesRepository.delete(it) }
            usersRepository.get(username)?.also { usersRepository.delete(it) }
        }

        // ── Auth ─────────────────────────────────────────────────────────────

        context("Authentication") {
            test("should return 401 when no Authorization header is provided") {
                mockMvc.perform(get("/integration-api/v1/app-groups"))
                    .andExpect(status().isUnauthorized)
            }

            test("should return 401 when Authorization header is not a Bearer token") {
                mockMvc.perform(
                    get("/integration-api/v1/app-groups")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                )
                    .andExpect(status().isUnauthorized)
            }

            test("should return 401 when Bearer token is invalid") {
                mockMvc.perform(
                    get("/integration-api/v1/app-groups")
                        .header("Authorization", "Bearer completely-wrong-token")
                )
                    .andExpect(status().isUnauthorized)
            }

            test("should accept a valid Bearer token") {
                mockMvc.perform(
                    get("/integration-api/v1/app-groups")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isOk)
            }
        }

        // ── GET /integration-api/v1/app-groups ───────────────────────────────

        context("GET /integration-api/v1/app-groups") {
            test("should list all app groups with available states and current state") {
                val result = mockMvc.perform(
                    get("/integration-api/v1/app-groups")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.appGroups").isArray)
                    .andExpect(jsonPath("$.appGroups[0].id").exists())
                    .andExpect(jsonPath("$.appGroups[0].name").value("Games"))
                    .andExpect(jsonPath("$.appGroups[0].color").exists())
                    .andExpect(jsonPath("$.appGroups[0].availableStates").isArray)
                    .andExpect(jsonPath("$.appGroups[0].currentState").exists())
                    .andExpect(jsonPath("$.appGroups[0].currentState.kind").exists())
                    .andExpect(jsonPath("$.appGroups[0].currentState.label").exists())
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("appGroups")
                groups.size() shouldBe 1

                val states = groups[0].get("availableStates")
                states.size() shouldBe 2
                val stateNames = (0 until states.size()).map { states[it].get("name").asText() }.toSet()
                stateNames shouldBe setOf("Locked", "Allowed")
            }

            test("should report currentState.kind as automatic when device has no forced state") {
                // Ensure no forced state on the device
                val device = devicesService.get(deviceId)
                device.update(DeviceDetailsUpdateDto(forcedDeviceState = set(null)))

                val result = mockMvc.perform(
                    get("/integration-api/v1/app-groups")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val currentState = response.get("appGroups")[0].get("currentState")
                currentState.get("kind").asText() shouldBe "automatic"
            }

            test("should report currentState as named when device matches a defined state") {
                // Apply "Locked" state directly via domain service
                val device = devicesService.get(deviceId)
                device.update(DeviceDetailsUpdateDto(forcedDeviceState = set(DeviceStateDto("LOCKED", null))))

                val result = mockMvc.perform(
                    get("/integration-api/v1/app-groups")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val currentState = response.get("appGroups")[0].get("currentState")
                currentState.get("kind").asText() shouldBe "named"
                currentState.get("label").asText() shouldBe "Locked"
                currentState.get("stateId").asText() shouldBe lockedStateId

                // Reset
                device.update(DeviceDetailsUpdateDto(forcedDeviceState = set(null)))
            }
        }

        // ── GET /integration-api/v1/app-groups/{groupId}/state ────────────────

        context("GET /integration-api/v1/app-groups/{groupId}/state") {
            test("should return current state for a specific group") {
                val result = mockMvc.perform(
                    get("/integration-api/v1/app-groups/$groupId/state")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.groupId").value(groupId))
                    .andExpect(jsonPath("$.currentState.kind").exists())
                    .andExpect(jsonPath("$.currentState.label").exists())
                    .andExpect(jsonPath("$.availableStates").isArray)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("groupId").asText() shouldBe groupId
                val states = response.get("availableStates")
                states.size() shouldBe 2
            }

            test("should return 404 for a non-existent group") {
                mockMvc.perform(
                    get("/integration-api/v1/app-groups/non-existent-group-id/state")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isNotFound)
            }
        }

        // ── POST /integration-api/v1/app-groups/{groupId}/apply-state/{stateId} ─

        context("POST /integration-api/v1/app-groups/{groupId}/apply-state/{stateId}") {
            test("should apply a group state and update device forced state") {
                // Ensure device starts with no forced state
                val device = devicesService.get(deviceId)
                device.update(DeviceDetailsUpdateDto(forcedDeviceState = set(null)))

                mockMvc.perform(
                    post("/integration-api/v1/app-groups/$groupId/apply-state/$lockedStateId")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.appliedStateName").value("Locked"))
                    .andExpect(jsonPath("$.appliedDevices").value(1))

                // Verify device state was actually written to Firestore
                val updatedDevice = devicesService.get(deviceId)
                val forcedState = updatedDevice.getDetails().forcedDeviceState
                forcedState shouldNotBe null
                forcedState!!.deviceState shouldBe "LOCKED"
            }

            test("should switch between states correctly") {
                // Apply "Allowed" state
                mockMvc.perform(
                    post("/integration-api/v1/app-groups/$groupId/apply-state/$allowedStateId")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.appliedStateName").value("Allowed"))

                val device = devicesService.get(deviceId)
                device.getDetails().forcedDeviceState?.deviceState shouldBe "ACTIVE"

                // Apply "Locked" state
                mockMvc.perform(
                    post("/integration-api/v1/app-groups/$groupId/apply-state/$lockedStateId")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.appliedStateName").value("Locked"))

                val device2 = devicesService.get(deviceId)
                device2.getDetails().forcedDeviceState?.deviceState shouldBe "LOCKED"

                // Cleanup
                device2.update(DeviceDetailsUpdateDto(forcedDeviceState = set(null)))
            }

            test("should return 404 for a non-existent group") {
                mockMvc.perform(
                    post("/integration-api/v1/app-groups/non-existent/apply-state/$lockedStateId")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isNotFound)
            }

            test("should return 404 for a non-existent state") {
                mockMvc.perform(
                    post("/integration-api/v1/app-groups/$groupId/apply-state/non-existent-state")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isNotFound)
            }

            test("should return 401 without a token") {
                mockMvc.perform(
                    post("/integration-api/v1/app-groups/$groupId/apply-state/$lockedStateId")
                )
                    .andExpect(status().isUnauthorized)
            }
        }

        // ── BFF token management ──────────────────────────────────────────────

        context("BFF integration API token management") {
            test("GET /bff/integration-api-settings should return the current token") {
                val result = mockMvc.perform(
                    get("/bff/integration-api-settings")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(username))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.token").exists())
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("token").asText() shouldBe token
            }

            test("POST /bff/integration-api-settings/regenerate-token should issue a new token") {
                val result = mockMvc.perform(
                    post("/bff/integration-api-settings/regenerate-token")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(username))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.token").exists())
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val newToken = response.get("token").asText()
                newToken.shouldNotBeBlank()
                newToken shouldNotBe token

                // Old token must now be rejected
                mockMvc.perform(
                    get("/integration-api/v1/app-groups")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isUnauthorized)

                // New token must be accepted
                mockMvc.perform(
                    get("/integration-api/v1/app-groups")
                        .header("Authorization", "Bearer $newToken")
                )
                    .andExpect(status().isOk)

                // Update local reference for subsequent tests
                token = newToken
            }

            test("POST /bff/integration-api-settings/revoke-token should remove the token") {
                mockMvc.perform(
                    post("/bff/integration-api-settings/revoke-token")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(username))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.token").value(null as String?))

                // Revoked token should now be rejected
                mockMvc.perform(
                    get("/integration-api/v1/app-groups")
                        .header("Authorization", "Bearer $token")
                )
                    .andExpect(status().isUnauthorized)

                // Re-issue a token for any remaining tests
                val user = usersService.get(username)
                val newToken = UUID.randomUUID().toString()
                user.updateIntegrationApiToken(newToken)
                token = newToken
            }
        }

        // ── User isolation ────────────────────────────────────────────────────

        context("User isolation") {
            test("a token from one user cannot access another user's groups") {
                val otherUsername = "isolation-user-${System.currentTimeMillis()}"
                usersService.createUser(otherUsername, "otherpass", AccessLevel.ADMIN)
                val otherUser = usersService.get(otherUsername)
                val otherToken = UUID.randomUUID().toString()
                otherUser.updateIntegrationApiToken(otherToken)

                // Create a group for the other user
                appGroupService.createAppGroup(otherUser, "OtherGroup")

                // Other user's token returns only their own groups (not the main user's "Games")
                val result = mockMvc.perform(
                    get("/integration-api/v1/app-groups")
                        .header("Authorization", "Bearer $otherToken")
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val names = response.get("appGroups").map { it.get("name").asText() }.toSet()
                names shouldBe setOf("OtherGroup")

                // Cleanup
                usersRepository.get(otherUsername)?.also { usersRepository.delete(it) }
            }
        }
    }
}

package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.hamcrest.Matchers
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
class V2AppGroupControllerIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var usersRepository: UsersRepository

    @Autowired
    private lateinit var usersService: UsersService

    @Autowired
    private lateinit var devicesService: DevicesService

    @Autowired
    private lateinit var devicesRepository: DevicesRepository

    @Autowired
    private lateinit var appGroupService: AppGroupService

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
        val username = "v2-user-" + System.currentTimeMillis()
        val password = "v2-pass"
        val instanceName = "V2 Test Device"
        val clientType = "TEST"

        lateinit var deviceId: DeviceId
        lateinit var token: String

        var groupId: String = ""
        val appKnown1 = "com.example.app1"
        val appKnown2 = "com.example.app2"
        val appUnknown = "com.example.unknown"

        beforeSpec {
            // Create user and device
            usersRepository.createUser(username, password.sha256(), AccessLevel.PARENT)
            val newDevice = devicesService.setupNewDevice(username, instanceName, clientType)
            deviceId = newDevice.deviceId
            token = newDevice.token

            // Provide known apps via v2 client-info
            val basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            val clientInfoBody = """
                {
                  "version": "v1.0.0",
                  "timezoneOffsetSeconds": 3600,
                  "reportIntervalSeconds": 60,
                  "availableStates": [
                    { "deviceState": "ACTIVE", "title": "Active", "icon": null, "description": null, "arguments": [] }
                  ],
                  "knownApps": {
                    "$appKnown1": { "appName": "Known App 1", "iconBase64Png": null },
                    "$appKnown2": { "appName": "Known App 2", "iconBase64Png": null }
                  }
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/client-info")
                    .header("Authorization", "Basic $basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(clientInfoBody)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("ok"))

            // Create an app group and add members (membership is per-device)
            val user = usersService.get(username)
            val group = appGroupService.createAppGroup(user, "Test Group")
            groupId = group.fetchDetails().id

            val deviceRef = devicesService.get(deviceId)
            group.addMember(deviceRef, appKnown1)
            group.addMember(deviceRef, appKnown2)
            group.addMember(deviceRef, appUnknown) // app not present in knownApps, to test fallback
        }

        afterSpec {
            // Cleanup device and user
            devicesRepository.get(deviceId)?.also { devicesRepository.delete(it) }
            usersRepository.get(username)?.also { usersRepository.delete(it) }
        }

        context("POST /api/v2/group-membership-for-device") {
            test("should return membership list for device with app names resolved from knownApps and fallback for unknown") {
                val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
                val requestBody = """{ "appGroupId": "$groupId" }"""

                val result = mockMvc.perform(
                    post("/api/v2/group-membership-for-device")
                        .header("Authorization", "Basic $apiV2Basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.appGroupId").value(groupId))
                    .andExpect(jsonPath("$.apps").isArray)
                    .andExpect(jsonPath("$.apps.length()", Matchers.greaterThanOrEqualTo(3)))
                    .andReturn()

                val json = objectMapper.readTree(result.response.contentAsString)
                val apps = json.get("apps")
                val paths = apps.map { it.get("appPath").asText() }
                paths.shouldContainAll(appKnown1, appKnown2, appUnknown)

                val byPath = apps.associateBy { it.get("appPath").asText() }
                byPath[appKnown1]!!.get("appName").asText() shouldBe "Known App 1"
                byPath[appKnown2]!!.get("appName").asText() shouldBe "Known App 2"
                // unknown app should fallback to technical id as name
                byPath[appUnknown]!!.get("appName").asText() shouldBe appUnknown

                // device info should be present
                val deviceName = byPath[appKnown1]!!.get("deviceName").asText()
                val deviceIdFromResponse = byPath[appKnown1]!!.get("deviceId").asText()
                deviceName.shouldNotBeBlank()
                UUID.fromString(deviceIdFromResponse) shouldBe deviceId
            }

            test("should return 500 for non-existing app group id") {
                val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
                val requestBody = """{ "appGroupId": "non-existing-${System.currentTimeMillis()}" }"""

                mockMvc.perform(
                    post("/api/v2/group-membership-for-device")
                        .header("Authorization", "Basic $apiV2Basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                )
                    .andExpect(status().isInternalServerError)
            }
        }

        context("POST /api/v2/groups-usage-report") {
            test("should aggregate screen time for apps in group across user's devices (single device here)") {
                // Send a daily report for today with usage of our apps
                val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
                val reportBody = """
                    {
                      "screenTime": 1000,
                      "applications": {
                        "$appKnown1": 300,
                        "$appKnown2": 600,
                        "$appUnknown": 100
                      }
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/v2/report")
                        .header("Authorization", "Basic $apiV2Basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody)
                )
                    .andExpect(status().isOk)

                val result = mockMvc.perform(
                    post("/api/v2/groups-usage-report")
                        .header("Authorization", "Basic $apiV2Basic")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.appGroups").isArray)
                    .andReturn()

                val json = objectMapper.readTree(result.response.contentAsString)
                val groups = json.get("appGroups")
                groups.size() shouldBe 1

                val group = groups[0]
                group.get("appGroupId").asText() shouldBe groupId
                group.get("appGroupName").asText() shouldBe "Test Group"
                group.get("totalTimeSeconds").asLong() shouldBe 1000L

                val apps = group.get("apps")
                apps shouldNotBe null
                apps.size() shouldBe 3

                val byPath = apps.associateBy { it.get("appPath").asText() }
                byPath[appKnown1]!!.get("uptimeSeconds").asLong() shouldBe 300L
                byPath[appKnown2]!!.get("uptimeSeconds").asLong() shouldBe 600L
                byPath[appUnknown]!!.get("uptimeSeconds").asLong() shouldBe 100L

                // device info should be embedded
                val deviceName = byPath[appKnown1]!!.get("deviceName").asText()
                val deviceIdFromResponse = byPath[appKnown1]!!.get("deviceId").asText()
                deviceName.shouldNotBeBlank()
                UUID.fromString(deviceIdFromResponse) shouldBe deviceId
            }

            test("should return empty apps when group has no members for device") {
                // Create a new empty group
                var emptyGroupId = ""
                val user = usersService.get(username)
                val group = appGroupService.createAppGroup(user, "Empty Group")
                emptyGroupId = group.fetchDetails().id

                val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
                val result = mockMvc.perform(
                    post("/api/v2/groups-usage-report")
                        .header("Authorization", "Basic $apiV2Basic")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val json = objectMapper.readTree(result.response.contentAsString)
                val groups = json.get("appGroups")
                // Now there should be 2 groups; one with apps and one empty
                groups.size() shouldBe 2
                val emptyGroup = groups.find { it.get("appGroupId").asText() == emptyGroupId }
                emptyGroup shouldNotBe null
                emptyGroup!!.get("apps").size() shouldBe 0
                emptyGroup.get("totalTimeSeconds").asLong() shouldBe 0L
            }
        }
    }
}

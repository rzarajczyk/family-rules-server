package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.cloud.firestore.Firestore
import kotlinx.datetime.Instant
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import pl.zarajczyk.familyrules.domain.DataRepository
import pl.zarajczyk.familyrules.domain.InstanceRef
import pl.zarajczyk.familyrules.domain.today
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DeviceHappyPathIntegrationSpec : BaseIntSpec() {

    @Autowired
    private lateinit var firestore: Firestore

    @Autowired
    private lateinit var dataRepository: DataRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    companion object {
        // Shared state across test methods
        private const val username = "admin"
        private const val password = "admin"
        private val instanceName = "test-device-${System.currentTimeMillis()}"
        private const val clientType = "TEST"
        private const val clientVersion = "v1.0.0"
        private const val timezoneOffsetSeconds = 3600
        private const val reportIntervalSeconds = 60
        
        private lateinit var instanceId: String
        private lateinit var token: String
        private lateinit var instanceRef: InstanceRef
        private lateinit var firstReportTimestamp: Instant
    }

    @Test
    @Order(1)
    fun `step 1 - should verify default user exists in database`() {
        val userDoc = firestore.collection("users").document(username).get().get()
        assertNotNull(userDoc, "Default user document should exist")
        assert(userDoc.exists()) { "Default user should exist in database" }
        assertEquals(username, userDoc.getString("username"), "Username should match")
        assertNotNull(userDoc.getString("passwordSha256"), "Password hash should exist")
    }

    @Test
    @Order(2)
    fun `step 2 - should register instance successfully`() {
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

    @Test
    @Order(3)
    fun `step 3 - should verify instance exists in database`() {
        val instanceDoc = firestore.collection("users")
            .document(username)
            .collection("instances")
            .document(instanceId)
            .get().get()
        
        assertNotNull(instanceDoc, "Instance document should exist")
        assert(instanceDoc.exists()) { "Instance should exist in database" }
        assertEquals(instanceId, instanceDoc.getString("instanceId"), "Instance ID should match")
        assertEquals(instanceName, instanceDoc.getString("instanceName"), "Instance name should match")
        assertEquals(clientType, instanceDoc.getString("clientType"), "Client type should match")
        assertEquals(false, instanceDoc.getBoolean("deleted"), "Instance should not be deleted")
    }

    @Test
    @Order(4)
    fun `step 4 - should send client-info successfully`() {
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

    @Test
    @Order(5)
    fun `step 5 - should verify client-info data in database`() {
        val instanceDoc = firestore.collection("users")
            .document(username)
            .collection("instances")
            .document(instanceId)
            .get().get()
        
        assertEquals(clientVersion, instanceDoc.getString("clientVersion"), 
            "Client version should be updated")
        assertEquals(timezoneOffsetSeconds.toLong(), instanceDoc.getLong("clientTimezoneOffsetSeconds"), 
            "Timezone offset should be updated")
        assertEquals(reportIntervalSeconds.toLong(), instanceDoc.getLong("reportIntervalSeconds"), 
            "Report interval should be updated")
        assertNotNull(instanceDoc.getString("knownApps"), 
            "Known apps should be stored")
        assertNotNull(instanceDoc.getString("deviceStates"), 
            "Device states should be stored")
    }

    @Test
    @Order(6)
    fun `step 6 - should send first report successfully`() {
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

    @Test
    @Order(7)
    fun `step 7 - should verify first report data in database`() {
        val today = today()
        instanceRef = dataRepository.findInstance(UUID.fromString(instanceId))!!
        
        val firstScreenTime = dataRepository.getScreenTimes(instanceRef, today)
        assertEquals(600L, firstScreenTime.screenTimeSeconds, "Screen time should match first report")
        assertEquals(400L, firstScreenTime.applicationsSeconds["com.example.app1"], 
            "App1 time should match first report")
        assertEquals(200L, firstScreenTime.applicationsSeconds["com.example.app2"], 
            "App2 time should match first report")
        assertNotNull(firstScreenTime.updatedAt, "Updated timestamp should exist after first report")
        
        firstReportTimestamp = firstScreenTime.updatedAt
        
        // Small delay to ensure timestamp changes
        Thread.sleep(100)
    }

    @Test
    @Order(8)
    fun `step 8 - should send second report successfully`() {
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

    @Test
    @Order(9)
    fun `step 9 - should verify second report overwrote first report in database`() {
        val today = today()
        
        val secondScreenTime = dataRepository.getScreenTimes(instanceRef, today)
        assertEquals(1200L, secondScreenTime.screenTimeSeconds, "Screen time should match second report")
        assertEquals(800L, secondScreenTime.applicationsSeconds["com.example.app1"], 
            "App1 time should match second report (overwritten)")
        assertEquals(400L, secondScreenTime.applicationsSeconds["com.example.app2"], 
            "App2 time should match second report (overwritten)")
        assertNotNull(secondScreenTime.updatedAt, "Updated timestamp should exist after second report")
        assert(secondScreenTime.updatedAt > firstReportTimestamp) { 
            "Second report timestamp should be after first report timestamp" 
        }
    }
}

//package pl.zarajczyk.familyrules
//
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.google.cloud.firestore.Firestore
//import kotlinx.datetime.Clock
//import kotlinx.datetime.LocalDate
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
//import org.springframework.boot.test.context.SpringBootTest
//import org.springframework.http.MediaType
//import org.springframework.test.context.ActiveProfiles
//import org.springframework.test.web.servlet.MockMvc
//import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
//import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
//import org.springframework.test.web.servlet.setup.MockMvcBuilders
//import org.springframework.web.context.WebApplicationContext
//import pl.zarajczyk.familyrules.api.v2.RegisterInstanceRequest
//import pl.zarajczyk.familyrules.api.v2.RegisterInstanceStatus
//import pl.zarajczyk.familyrules.api.v2.ReportRequest
//import pl.zarajczyk.familyrules.shared.DataRepository
//import pl.zarajczyk.familyrules.shared.InstanceId
//import pl.zarajczyk.familyrules.shared.today
//import java.util.*
//
//@SpringBootTest
//@AutoConfigureWebMvc
//@ActiveProfiles("test")
//class IntegrationTest {
//
//    @Autowired
//    private lateinit var webApplicationContext: WebApplicationContext
//
//    @Autowired
//    private lateinit var dataRepository: DataRepository
//
//    @Autowired
//    private lateinit var firestore: Firestore
//
//    @Autowired
//    private lateinit var objectMapper: ObjectMapper
//
//    private lateinit var mockMvc: MockMvc
//
//    @BeforeEach
//    fun setup() {
//        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
//        // Clear any existing test data
//        clearTestData()
//    }
//
//    @Test
//    fun `should register instance and save report successfully`() {
//        // Given
//        val username = "admin"
//        val password = "admin"
//        val instanceName = "test-instance-${System.currentTimeMillis()}"
//        val clientType = "TEST"
//        val screenTimeSeconds = 600L
//        val applicationsSeconds = mapOf("app1" to 400L, "app2" to 200L)
//
//        // Step 1: Register instance
//        val registerRequest = RegisterInstanceRequest(instanceName, clientType)
//        val registerResponse = mockMvc.perform(
//            post("/api/v2/register-instance")
//                .contentType(MediaType.APPLICATION_JSON)
//                .header("Authorization", "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}")
//                .content(objectMapper.writeValueAsString(registerRequest))
//        )
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$.status").value(RegisterInstanceStatus.SUCCESS.name))
//            .andExpect(jsonPath("$.instanceId").exists())
//            .andExpect(jsonPath("$.token").exists())
//            .andReturn()
//
//        // Extract instance ID and token from response
//        val responseContent = registerResponse.response.contentAsString
//        val responseJson = objectMapper.readTree(responseContent)
//        val instanceId = responseJson.get("instanceId").asText()
//        val token = responseJson.get("token").asText()
//
//        // Verify instance was created in database
//        val createdInstance = dataRepository.getInstance(UUID.fromString(instanceId))
//        assert(createdInstance != null) { "Instance should be created in database" }
//        assert(createdInstance!!.name == instanceName) { "Instance name should match" }
//        assert(createdInstance.clientType == clientType) { "Client type should match" }
//
//        // Step 2: Save report
//        val reportRequest = ReportRequest(
//            screenTimeSeconds = screenTimeSeconds,
//            applicationsSeconds = applicationsSeconds
//        )
//
//        mockMvc.perform(
//            post("/api/v2/report")
//                .contentType(MediaType.APPLICATION_JSON)
//                .header("Authorization", "Basic ${Base64.getEncoder().encodeToString("$instanceId:$token".toByteArray())}")
//                .content(objectMapper.writeValueAsString(reportRequest))
//        )
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$.deviceState").exists())
//
//        // Step 3: Verify report was saved in database
//        val today = today()
//        val savedScreenTime = dataRepository.getScreenTimes(UUID.fromString(instanceId), today)
//
//        assert(savedScreenTime.screenTimeSeconds == screenTimeSeconds) {
//            "Screen time should match. Expected: $screenTimeSeconds, Actual: ${savedScreenTime.screenTimeSeconds}"
//        }
//        assert(savedScreenTime.applicationsSeconds == applicationsSeconds) {
//            "Applications time should match. Expected: $applicationsSeconds, Actual: ${savedScreenTime.applicationsSeconds}"
//        }
//        // Verify that the timestamp is recent (within the last minute)
//        val now = Clock.System.now()
//        val timeDiff = now.toEpochMilliseconds() - savedScreenTime.updatedAt.toEpochMilliseconds()
//        assert(timeDiff >= 0 && timeDiff < 60000) {
//            "Updated timestamp should be recent. Time difference: ${timeDiff}ms"
//        }
//    }
//
//    @Test
//    fun `should handle invalid credentials during instance registration`() {
//        // Given
//        val invalidUsername = "invalid"
//        val invalidPassword = "invalid"
//        val instanceName = "test-instance"
//        val clientType = "TEST"
//
//        val registerRequest = RegisterInstanceRequest(instanceName, clientType)
//
//        // When & Then
//        mockMvc.perform(
//            post("/api/v2/register-instance")
//                .contentType(MediaType.APPLICATION_JSON)
//                .header("Authorization", "Basic ${Base64.getEncoder().encodeToString("$invalidUsername:$invalidPassword".toByteArray())}")
//                .content(objectMapper.writeValueAsString(registerRequest))
//        )
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$.status").value(RegisterInstanceStatus.INVALID_PASSWORD.name))
//    }
//
//    @Test
//    fun `should handle duplicate instance name`() {
//        // Given
//        val username = "admin"
//        val password = "admin"
//        val instanceName = "duplicate-instance"
//        val clientType = "TEST"
//
//        val registerRequest = RegisterInstanceRequest(instanceName, clientType)
//
//        // Register first instance
//        mockMvc.perform(
//            post("/api/v2/register-instance")
//                .contentType(MediaType.APPLICATION_JSON)
//                .header("Authorization", "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}")
//                .content(objectMapper.writeValueAsString(registerRequest))
//        )
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$.status").value(RegisterInstanceStatus.SUCCESS.name))
//
//        // Try to register second instance with same name
//        mockMvc.perform(
//            post("/api/v2/register-instance")
//                .contentType(MediaType.APPLICATION_JSON)
//                .header("Authorization", "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}")
//                .content(objectMapper.writeValueAsString(registerRequest))
//        )
//            .andExpect(status().isOk)
//            .andExpect(jsonPath("$.status").value(RegisterInstanceStatus.INSTANCE_ALREADY_EXISTS.name))
//    }
//
//    @Test
//    fun `should handle invalid instance credentials during report saving`() {
//        // Given
//        val invalidInstanceId = UUID.randomUUID().toString()
//        val invalidToken = "invalid-token"
//        val reportRequest = ReportRequest(
//            screenTimeSeconds = 600L,
//            applicationsSeconds = mapOf("app1" to 400L)
//        )
//
//        // When & Then
//        mockMvc.perform(
//            post("/api/v2/report")
//                .contentType(MediaType.APPLICATION_JSON)
//                .header("Authorization", "Basic ${Base64.getEncoder().encodeToString("$invalidInstanceId:$invalidToken".toByteArray())}")
//                .content(objectMapper.writeValueAsString(reportRequest))
//        )
//            .andExpect(status().isUnauthorized)
//    }
//
//    private fun clearTestData() {
//        // Clear all test data from Firestore
//        try {
//            // Delete all users (this will cascade to instances and screen times)
//            val users = firestore.collection("users").get().get()
//            users.documents.forEach { userDoc ->
//                userDoc.reference.delete().get()
//            }
//        } catch (e: Exception) {
//            // Ignore errors during cleanup - test environment might be empty
//        }
//    }
//}

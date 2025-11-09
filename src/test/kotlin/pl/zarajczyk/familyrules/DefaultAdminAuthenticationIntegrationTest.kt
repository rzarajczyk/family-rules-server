package pl.zarajczyk.familyrules

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.*
import org.testcontainers.gcloud.FirestoreEmulatorContainer


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DefaultAdminAuthenticationIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    companion object {
        // Firestore emulator container bound to localhost:8080
        @Container
        @JvmStatic
        val firestore: FirestoreEmulatorContainer =
            FirestoreEmulatorContainer("gcr.io/google.com/cloudsdktool/google-cloud-cli:546.0.0-emulators")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            // Ensure container is started before accessing endpoint
            firestore.start()
            registry.add("firestore.emulator-host") { firestore.emulatorEndpoint }
        }
    }

    @Test
    fun `should accept default admin username and password on empty database`() {
        val username = "admin"
        val password = "admin"

        val basic = Base64.getEncoder().encodeToString("$username:$password".toByteArray())

        val jsonBody = "{" +
                "\"instanceName\":\"it-default-admin-${System.currentTimeMillis()}\"," +
                "\"clientType\":\"TEST\"" +
                "}" 

        mockMvc.perform(
            post("/api/v2/register-instance")
                .header("Authorization", "Basic $basic")
                .contentType("application/json")
                .content(jsonBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.instanceId").exists())
            .andExpect(jsonPath("$.token").exists())
    }
}

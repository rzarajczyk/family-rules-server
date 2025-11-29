package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
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
class BffGroupStatesControllerIntegrationSpec : FunSpec() {

    override fun extensions(): List<Extension> = listOf(SpringExtension)

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

    @Autowired
    private lateinit var groupStateService: GroupStateService

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
        val username = "testuser-groupstates"
        val password = "testpass"
        lateinit var groupId: String

        beforeSpec {
            // Create user
            usersService.createUser(username, password, AccessLevel.ADMIN)

            // Create app group
            val user = usersService.get(username)
            val group = appGroupService.createAppGroup(user, "Test Group")
            groupId = group.fetchDetails().id
        }

        afterSpec {
            // Cleanup
            usersRepository.get(username)?.also { usersRepository.delete(it) }
        }

        test("should create a group state") {
            val requestBody = """
                {
                    "name": "Locked State",
                    "deviceStates": {}
                }
            """.trimIndent()

            val result = mockMvc.perform(
                post("/bff/app-groups/$groupId/states")
                    .with(SecurityMockMvcRequestPostProcessors.user(username))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.state.id").exists())
                .andExpect(jsonPath("$.state.name").value("Locked State"))
                .andReturn()

            val response = objectMapper.readTree(result.response.contentAsString)
            val stateId = response.get("state").get("id").asText()
            stateId shouldNotBe null
        }

        test("should list group states") {
            mockMvc.perform(
                get("/bff/app-groups/$groupId/states")
                    .with(SecurityMockMvcRequestPostProcessors.user(username))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.states").isArray)
        }

        test("should get devices for states configuration") {
            mockMvc.perform(
                get("/bff/app-groups/$groupId/devices-for-states")
                    .with(SecurityMockMvcRequestPostProcessors.user(username))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.devices").isArray)
        }
    }
}

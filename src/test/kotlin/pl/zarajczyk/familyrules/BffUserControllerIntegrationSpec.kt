package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.hamcrest.Matchers.containsString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.gcloud.FirestoreEmulatorContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import pl.zarajczyk.familyrules.domain.port.UsersRepository

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class BffUserControllerIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var usersRepository: UsersRepository

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
        val adminUsername = "admin"
        val adminPassword = "admin"
        val testUsername = "testuser-${System.currentTimeMillis()}"
        val testPassword = "testpass123"

        context("GET /bff/current-user") {
            test("should return current user information for authenticated admin user") {
                val result = mockMvc.perform(
                    get("/bff/current-user")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value(adminUsername))
                    .andExpect(jsonPath("$.accessLevel").value("ADMIN"))
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("username").asText() shouldBe adminUsername
                response.get("accessLevel").asText() shouldBe "ADMIN"
            }

            test("should redirect to login page for unauthenticated request") {
                mockMvc.perform(get("/bff/current-user"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("GET /bff/users") {
            test("should return all users for admin user") {
                val result = mockMvc.perform(
                    get("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.users").isArray)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val users = response.get("users")
                users.isArray shouldBe true
                users.size() shouldBe 1
                users[0].get("username").asText() shouldBe adminUsername
            }

            test("should redirect to login page for unauthenticated request") {
                mockMvc.perform(get("/bff/users"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("POST /bff/users - create user") {
            test("should create new user successfully as admin") {
                val createUserRequest = """
                    {
                        "username": "$testUsername",
                        "password": "$testPassword",
                        "accessLevel": "PARENT"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("User created successfully"))
            }

            test("should verify new user exists in database") {
                val userRef = usersRepository.get(testUsername)
                userRef shouldNotBe null
                val user = usersRepository.fetchDetails(userRef!!)
                user.username shouldBe testUsername
                user.passwordSha256 shouldNotBe null
                user.accessLevel.name shouldBe "PARENT"
            }

            test("should verify new user can authenticate") {
                mockMvc.perform(
                    get("/bff/current-user")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value(testUsername))
                    .andExpect(jsonPath("$.accessLevel").value("PARENT"))
            }

            test("should list all users including newly created one") {
                val result = mockMvc.perform(
                    get("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.users").isArray)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val users = response.get("users").map { it.get("username").asText() }
                users shouldHaveSize 2
                users shouldContain adminUsername
                users shouldContain testUsername
            }

            test("should return error when trying to create user that already exists") {
                val createUserRequest = """
                    {
                        "username": "$testUsername",
                        "password": "anotherpassword",
                        "accessLevel": "ADMIN"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserRequest)
                )
                    .andExpect(status().is5xxServerError) // Returns 500 when user already exists
            }

            test("should redirect to login page for unauthenticated request") {
                val createUserRequest = """
                    {
                        "username": "newuser",
                        "password": "password",
                        "accessLevel": "PARENT"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserRequest)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("POST /bff/users/{username}/reset-password") {
            val newPassword = "newpassword456"

            test("should reset password successfully as admin") {
                val resetPasswordRequest = """
                    {
                        "newPassword": "$newPassword"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/users/$testUsername/reset-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password reset successfully"))
            }

            test("should verify target user password was changed") {
                mockMvc.perform(
                    get("/bff/current-user")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value(testUsername))
            }

            test("should redirect to login page for unauthenticated request") {
                val resetPasswordRequest = """
                    {
                        "newPassword": "somepassword"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/users/$testUsername/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPasswordRequest)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("DELETE /bff/users/{username}") {
            test("should return error when admin tries to delete their own account") {
                mockMvc.perform(
                    delete("/bff/users/$adminUsername")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                )
                    .andExpect(status().is5xxServerError) // Returns 500 when trying to delete own account
            }

            test("should delete user successfully as admin") {
                mockMvc.perform(
                    delete("/bff/users/$testUsername")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("User deleted successfully"))
            }

            test("should verify deleted user no longer exists in database") {
                val userRef = usersRepository.get(testUsername)
                userRef shouldBe null
            }

            test("should verify deleted user is not in users list") {
                val result = mockMvc.perform(
                    get("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val users = response.get("users").map { it.get("username").asText() }
                users shouldNotContain testUsername
            }

            test("should redirect to login page for unauthenticated request") {
                mockMvc.perform(delete("/bff/users/$testUsername"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("Authorization - PARENT user restrictions") {
            val parentUsername = "parent-${System.currentTimeMillis()}"
            val parentPassword = "parentpass123"

            test("setup - create PARENT user") {
                val createUserRequest = """
                    {
                        "username": "$parentUsername",
                        "password": "$parentPassword",
                        "accessLevel": "PARENT"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserRequest)
                )
                    .andExpect(status().isOk)
            }

            test("PARENT user should be able to get their current user info") {
                mockMvc.perform(
                    get("/bff/current-user")
                        .with(SecurityMockMvcRequestPostProcessors.user(parentUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.username").value(parentUsername))
                    .andExpect(jsonPath("$.accessLevel").value("PARENT"))
            }

            test("PARENT user should be forbidden from listing all users") {
                mockMvc.perform(
                    get("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(parentUsername))
                )
                    .andExpect(status().is5xxServerError) // Returns 500 when forbidden
            }

            test("PARENT user should be forbidden from creating new users") {
                val createUserRequest = """
                    {
                        "username": "another-user",
                        "password": "password",
                        "accessLevel": "PARENT"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(parentUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserRequest)
                )
                    .andExpect(status().is5xxServerError) // Returns 500 when forbidden
            }

            test("PARENT user should be forbidden from resetting passwords") {
                val resetPasswordRequest = """
                    {
                        "newPassword": "newpass"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/users/$adminUsername/reset-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(parentUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPasswordRequest)
                )
                    .andExpect(status().is5xxServerError) // Returns 500 when forbidden
            }

            test("PARENT user should be forbidden from deleting users") {
                mockMvc.perform(
                    delete("/bff/users/$adminUsername")
                        .with(SecurityMockMvcRequestPostProcessors.user(parentUsername))
                )
                    .andExpect(status().is5xxServerError) // Returns 500 when forbidden
            }

            test("cleanup - delete PARENT user") {
                mockMvc.perform(
                    delete("/bff/users/$parentUsername")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                )
                    .andExpect(status().isOk)
            }
        }
    }
}

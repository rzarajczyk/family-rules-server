package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.gcloud.FirestoreEmulatorContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import pl.zarajczyk.familyrules.domain.sha256

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class BffSettingsControllerIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var usersRepository: UsersRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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
        val newPassword = "newpass456"

        context("POST /bff/change-password") {
            test("setup - create test user") {
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
            }

            test("should change password successfully with correct current password") {
                val changePasswordRequest = """
                    {
                        "currentPassword": "$testPassword",
                        "newPassword": "$newPassword"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password changed successfully"))
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("success").asBoolean() shouldBe true
                response.get("message").asText() shouldBe "Password changed successfully"
            }

            test("should verify password was changed in database") {
                val userRef = usersRepository.get(testUsername)
                userRef shouldNotBe null
                val user = usersRepository.fetchDetails(userRef!!, includePasswordHash = true)
                user.passwordSha256 shouldBe newPassword.sha256()
                user.passwordSha256 shouldNotBe testPassword.sha256()
            }

            test("should return error when current password is incorrect") {
                val changePasswordRequest = """
                    {
                        "currentPassword": "wrongpassword",
                        "newPassword": "anotherpassword"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Current password is incorrect"))
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("success").asBoolean() shouldBe false
                response.get("message").asText() shouldBe "Current password is incorrect"
            }

            test("should verify password was not changed after incorrect attempt") {
                val userRef = usersRepository.get(testUsername)
                userRef shouldNotBe null
                // Password should still be the new password from successful change
                usersRepository.fetchDetails(userRef!!, includePasswordHash = true).passwordSha256 shouldBe newPassword.sha256()
            }

            test("should change password again successfully with new current password") {
                val anotherNewPassword = "yetanotherpass789"
                val changePasswordRequest = """
                    {
                        "currentPassword": "$newPassword",
                        "newPassword": "$anotherNewPassword"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password changed successfully"))

                val userRef = usersRepository.get(testUsername)
                userRef shouldNotBe null
                usersRepository.fetchDetails(userRef!!, includePasswordHash = true).passwordSha256 shouldBe anotherNewPassword.sha256()
            }

            test("should redirect to login page for unauthenticated request") {
                val changePasswordRequest = """
                    {
                        "currentPassword": "somepassword",
                        "newPassword": "newpassword"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }

            test("admin user should be able to change their own password") {
                val adminNewPassword = "adminNewPass123"
                val changePasswordRequest = """
                    {
                        "currentPassword": "$adminPassword",
                        "newPassword": "$adminNewPassword"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password changed successfully"))

                val adminRef = usersRepository.get(adminUsername)
                adminRef shouldNotBe null
                usersRepository.fetchDetails(adminRef!!, includePasswordHash = true).passwordSha256 shouldBe adminNewPassword.sha256()
            }

            test("should restore admin password for cleanup") {
                val changePasswordRequest = """
                    {
                        "currentPassword": "adminNewPass123",
                        "newPassword": "$adminPassword"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
            }

            test("cleanup - delete test user") {
                mockMvc.perform(
                    post("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"username":"$testUsername","password":"anypass","accessLevel":"PARENT"}""")
                )

                usersRepository.get(testUsername)?.let { usersRepository.delete(it) }
            }
        }

        context("POST /bff/change-password - Edge Cases") {
            val edgeTestUsername = "edgeuser-${System.currentTimeMillis()}"
            val edgeTestPassword = "edgepass123"

            test("setup - create edge test user") {
                val createUserRequest = """
                    {
                        "username": "$edgeTestUsername",
                        "password": "$edgeTestPassword",
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

            test("should handle empty current password") {
                val changePasswordRequest = """
                    {
                        "currentPassword": "",
                        "newPassword": "newpass"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(edgeTestUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Current password is incorrect"))
            }

            test("should handle empty new password") {
                val changePasswordRequest = """
                    {
                        "currentPassword": "$edgeTestPassword",
                        "newPassword": ""
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(edgeTestUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password changed successfully"))

                // Verify empty password was actually set (though not recommended in production)
                val edgeRef = usersRepository.get(edgeTestUsername)
                edgeRef shouldNotBe null
                usersRepository.fetchDetails(edgeRef!!, includePasswordHash = true).passwordSha256 shouldBe "".sha256()
            }

            test("should reset password back to test password for further tests") {
                val changePasswordRequest = """
                    {
                        "currentPassword": "",
                        "newPassword": "$edgeTestPassword"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(edgeTestUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
            }

            test("should handle very long password") {
                val longPassword = "a".repeat(1000)
                val changePasswordRequest = """
                    {
                        "currentPassword": "$edgeTestPassword",
                        "newPassword": "$longPassword"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(edgeTestUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password changed successfully"))

                val edgeRef2 = usersRepository.get(edgeTestUsername)
                edgeRef2 shouldNotBe null
                usersRepository.fetchDetails(edgeRef2!!, includePasswordHash = true).passwordSha256 shouldBe longPassword.sha256()
            }

            test("should handle special characters in password") {
                val specialPassword = "p@ssw0rd!#\$%^&*()_+-={}[]|:;<>?,./~`"
                val changePasswordRequest = """
                    {
                        "currentPassword": "${"a".repeat(1000)}",
                        "newPassword": "$specialPassword"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(edgeTestUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password changed successfully"))

                val edgeRef3 = usersRepository.get(edgeTestUsername)
                edgeRef3 shouldNotBe null
                usersRepository.fetchDetails(edgeRef3!!, includePasswordHash = true).passwordSha256 shouldBe specialPassword.sha256()
            }

            test("should handle unicode characters in password") {
                val unicodePassword = "пароль密码🔒🔑"
                val changePasswordRequest = """
                    {
                        "currentPassword": "p@ssw0rd!#${'$'}%^&*()_+-={}[]|:;<>?,./~`",
                        "newPassword": "$unicodePassword"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(edgeTestUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password changed successfully"))

                val edgeRef4 = usersRepository.get(edgeTestUsername)
                edgeRef4 shouldNotBe null
                usersRepository.fetchDetails(edgeRef4!!, includePasswordHash = true).passwordSha256 shouldBe unicodePassword.sha256()
            }

            test("should handle changing to same password") {
                val changePasswordRequest = """
                    {
                        "currentPassword": "пароль密码🔒🔑",
                        "newPassword": "пароль密码🔒🔑"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(edgeTestUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password changed successfully"))
            }

            test("cleanup - delete edge test user") {
                usersRepository.get(edgeTestUsername)?.let { usersRepository.delete(it) }
            }
        }

        context("POST /bff/change-password - Multiple users isolation") {
            val user1Username = "user1-${System.currentTimeMillis()}"
            val user1Password = "user1pass"
            val user2Username = "user2-${System.currentTimeMillis()}"
            val user2Password = "user2pass"

            test("setup - create two test users") {
                val createUser1Request = """
                    {
                        "username": "$user1Username",
                        "password": "$user1Password",
                        "accessLevel": "PARENT"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUser1Request)
                )
                    .andExpect(status().isOk)

                val createUser2Request = """
                    {
                        "username": "$user2Username",
                        "password": "$user2Password",
                        "accessLevel": "PARENT"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUser2Request)
                )
                    .andExpect(status().isOk)
            }

            test("user1 should change their own password") {
                val changePasswordRequest = """
                    {
                        "currentPassword": "$user1Password",
                        "newPassword": "user1newpass"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(user1Username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
            }

            test("user1 password change should not affect user2") {
                val user2Ref = usersRepository.get(user2Username)
                user2Ref shouldNotBe null
                usersRepository.fetchDetails(user2Ref!!, includePasswordHash = true).passwordSha256 shouldBe user2Password.sha256()
            }

            test("user2 should still be able to use their original password") {
                val changePasswordRequest = """
                    {
                        "currentPassword": "$user2Password",
                        "newPassword": "user2newpass"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/change-password")
                        .with(SecurityMockMvcRequestPostProcessors.user(user2Username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
            }

            test("verify both users have different passwords") {
                val user1Ref = usersRepository.get(user1Username)
                val user2Ref = usersRepository.get(user2Username)
                user1Ref shouldNotBe null
                user2Ref shouldNotBe null

                val user1Hash = usersRepository.fetchDetails(user1Ref!!, includePasswordHash = true).passwordSha256
                val user2Hash = usersRepository.fetchDetails(user2Ref!!, includePasswordHash = true).passwordSha256

                user1Hash shouldNotBe user2Hash
                user1Hash shouldBe "user1newpass".sha256()
                user2Hash shouldBe "user2newpass".sha256()
            }

            test("cleanup - delete test users") {
                usersRepository.get(user1Username)?.let { usersRepository.delete(it) }
                usersRepository.get(user2Username)?.let { usersRepository.delete(it) }
            }
        }
    }
}

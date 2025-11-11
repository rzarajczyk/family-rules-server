package pl.zarajczyk.familyrules

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotBeEmpty
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
import pl.zarajczyk.familyrules.domain.AccessLevel
import pl.zarajczyk.familyrules.domain.AppGroupRepository
import pl.zarajczyk.familyrules.domain.DataRepository
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.UserRef
import pl.zarajczyk.familyrules.domain.UsersRepository

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class BffAppGroupsControllerIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var appGroupRepository: AppGroupRepository

    @Autowired
    private lateinit var usersRepository: UsersRepository

    @Autowired
    private lateinit var dataRepository: DataRepository

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
        val testUsername = "testuser-${System.currentTimeMillis()}"
        lateinit var deviceId: DeviceId
        lateinit var userRef: UserRef

        beforeSpec {
            userRef = usersRepository.createUser(testUsername, "pass", AccessLevel.PARENT)
            deviceId = dataRepository.setupNewInstance(testUsername, "Test instance", "TEST").instanceId
        }

        afterSpec {
            dataRepository.deleteInstance(dataRepository.findInstance(deviceId)!!)
            usersRepository.delete(userRef)
        }

        context("POST /bff/app-groups - create app group") {
            test("should create app group successfully") {
                val createGroupRequest = """
                    {
                        "name": "Social Media"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.group").exists())
                    .andExpect(jsonPath("$.group.id").exists())
                    .andExpect(jsonPath("$.group.name").value("Social Media"))
                    .andExpect(jsonPath("$.group.color").exists())
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val group = response.get("group")
                group.get("id").asText().shouldNotBeBlank()
                group.get("name").asText() shouldBe "Social Media"
                group.get("color").asText().shouldNotBeBlank()

                val groupId = group.get("id").asText()

                val appGroupRef = appGroupRepository.get(userRef, groupId)!!
                val dto = appGroupRepository.fetchDetails(appGroupRef)

                dto.id shouldBe groupId
                dto.name shouldBe "Social Media"
                dto.color.shouldNotBeBlank()
            }

            test("should create multiple app groups with different names") {
                val createGroupRequest = """
                    {
                        "name": "Games"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.group.name").value("Games"))

                val appGroups = appGroupRepository.getAll(userRef)
                appGroups shouldHaveSize 2
                val groupNames = appGroups
                    .map { appGroupRepository.fetchDetails(it) }
                    .map { it.name }

                groupNames shouldContain "Social Media"
                groupNames shouldContain "Games"
            }

            test("should redirect to login page for unauthenticated request") {
                val createGroupRequest = """
                    {
                        "name": "Test Group"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/app-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupRequest)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("GET /bff/app-groups - list app groups") {
            test("should return all app groups for user") {
                val result = mockMvc.perform(
                    get("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.groups").isArray)
                    .andExpect(jsonPath("$.groups.length()").value(2))
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("groups")
                groups.isArray shouldBe true
                groups shouldHaveSize 2

                val groupNames = groups.map { it.get("name").asText() }
                groupNames shouldContain "Social Media"
                groupNames shouldContain "Games"
            }

            test("should return empty list for user with no app groups") {
                val newUsername = "newuser-${System.currentTimeMillis()}"
                val createUserRequest = """
                    {
                        "username": "$newUsername",
                        "password": "password123",
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

                val result = mockMvc.perform(
                    get("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(newUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.groups").isArray)
                    .andExpect(jsonPath("$.groups.length()").value(0))
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("groups")
                groups.toList().shouldBeEmpty()

                // Cleanup
                val ref = usersRepository.get(newUsername)!!
                usersRepository.delete(ref)
            }

            test("should redirect to login page for unauthenticated request") {
                mockMvc.perform(get("/bff/app-groups"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("PUT /bff/app-groups/{groupId} - rename app group") {
            var groupIdToRename: String? = null

            test("should get group ID for renaming test") {
                val result = mockMvc.perform(
                    get("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("groups")
                groupIdToRename = groups.find { it.get("name").asText() == "Social Media" }
                    ?.get("id")?.asText()

                groupIdToRename shouldNotBe null
            }

            test("should rename app group successfully") {
                val renameRequest = """
                    {
                        "newName": "Social Networks"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    put("/bff/app-groups/$groupIdToRename")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(renameRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn()
            }

            test("should verify renamed group in database") {
                val appGroupRef = appGroupRepository.get(userRef, groupIdToRename!!)!!
                val dto = appGroupRepository.fetchDetails(appGroupRef)

                dto.id shouldBe groupIdToRename
                dto.name shouldBe "Social Networks"
                dto.color.shouldNotBeEmpty()
            }

            test("should verify renamed group in list") {
                val result = mockMvc.perform(
                    get("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("groups")
                val groupNames = groups.map { it.get("name").asText() }
                groupNames shouldContain "Social Networks"
                groupNames shouldNotContain "Social Media"
                groupNames shouldContain "Games"
            }

            test("should redirect to login page for unauthenticated request") {
                val renameRequest = """
                    {
                        "newName": "New Name"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/bff/app-groups/$groupIdToRename")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(renameRequest)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("DELETE /bff/app-groups/{groupId} - delete app group") {
            var groupIdToDelete: String? = null

            test("should create a new group to delete") {
                val createGroupRequest = """
                    {
                        "name": "To Be Deleted"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupRequest)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                groupIdToDelete = response.get("group").get("id").asText()
                groupIdToDelete.shouldNotBeBlank()
            }

            test("should verify group exists before deletion") {
                val result = mockMvc.perform(
                    get("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("groups")
                groups shouldHaveSize 3
                val groupNames = groups.map { it.get("name").asText() }
                groupNames shouldContain "To Be Deleted"
            }

            test("should delete app group successfully") {
                mockMvc.perform(
                    delete("/bff/app-groups/$groupIdToDelete")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
            }

            test("should verify group is deleted from database") {
                val appGroupRef = appGroupRepository.get(userRef, groupIdToDelete!!)

                appGroupRef shouldBe null
            }

            test("should verify deleted group not in list") {
                val result = mockMvc.perform(
                    get("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("groups")
                groups shouldHaveSize 2
                val groupNames = groups.map { it.get("name").asText() }
                groupNames shouldNotContain "To Be Deleted"
                groupNames shouldContain "Social Networks"
                groupNames shouldContain "Games"
            }

            test("should redirect to login page for unauthenticated request") {
                mockMvc.perform(delete("/bff/app-groups/$groupIdToDelete"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("POST /bff/app-groups/{groupId}/apps - add app to group") {
            var groupId: String? = null
            val appPath = "com.example.testapp"

            test("should get group ID for app management tests") {
                val result = mockMvc.perform(
                    get("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("groups")
                groupId = groups.find { it.get("name").asText() == "Games" }
                    ?.get("id")?.asText()

                groupId shouldNotBe null
            }

            test("should add app to group successfully") {
                val addAppRequest = """
                    {
                        "instanceId": "$deviceId",
                        "appPath": "$appPath"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/app-groups/$groupId/apps")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addAppRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
            }

            test("should verify app membership exists in database") {
                val appGroupRef = appGroupRepository.get(userRef, groupId!!)!!
                val deviceRef = dataRepository.findInstance(deviceId)!!
                val members = appGroupRepository.getMembers(appGroupRef, deviceRef)

                members shouldHaveSize 1
                members shouldContain appPath
            }

            test("should add multiple apps to same group") {
                val appPath2 = "com.example.anotherapp"
                val addAppRequest = """
                    {
                        "instanceId": "$deviceId",
                        "appPath": "$appPath2"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/app-groups/$groupId/apps")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addAppRequest)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))

                val appGroupRef = appGroupRepository.get(userRef, groupId!!)!!
                val deviceRef = dataRepository.findInstance(deviceId)!!
                val members = appGroupRepository.getMembers(appGroupRef, deviceRef)

                members shouldHaveSize 2
                members shouldContain appPath
                members shouldContain appPath2
            }

            test("should redirect to login page for unauthenticated request") {
                val addAppRequest = """
                    {
                        "instanceId": "$deviceId",
                        "appPath": "com.example.test"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/bff/app-groups/$groupId/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addAppRequest)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("DELETE /bff/app-groups/{groupId}/apps/{appPath} - remove app from group") {
            var groupId: String? = null
            val appPath = "com.example.testapp"

            test("should get group ID for removal tests") {
                val result = mockMvc.perform(
                    get("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("groups")
                groupId = groups.find { it.get("name").asText() == "Games" }
                    ?.get("id")?.asText()

                groupId shouldNotBe null
            }

            test("should verify app exists in group before removal") {
                val appGroup = appGroupRepository.get(userRef, groupId!!)!!
                val device = dataRepository.findInstance(deviceId)!!
                val members = appGroupRepository.getMembers(appGroup, device)

                members shouldHaveSize 2
                members shouldContain appPath
                members shouldContain "com.example.anotherapp"
            }

            test("should remove app from group successfully") {
                mockMvc.perform(
                    delete("/bff/app-groups/$groupId/apps/$appPath")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .param("instanceId", deviceId.toString())
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
            }

            test("should verify app membership is removed from database") {
                val appGroup = appGroupRepository.get(userRef, groupId!!)!!
                val device = dataRepository.findInstance(deviceId)!!
                val members = appGroupRepository.getMembers(appGroup, device)

                members shouldHaveSize 1
                members shouldContain "com.example.anotherapp"
            }

            test("should redirect to login page for unauthenticated request") {
                mockMvc.perform(
                    delete("/bff/app-groups/$groupId/apps/$appPath")
                        .param("instanceId", deviceId.toString())
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(header().string("Location", containsString("/gui/login.html")))
            }
        }

        context("User isolation - app groups") {
            val user1Username = "user1-${System.currentTimeMillis()}"
            val user2Username = "user2-${System.currentTimeMillis()}"
            val password = "testpass123"
            var user1GroupId: String? = null
            var user2GroupId: String? = null

            test("setup - create two test users") {
                listOf(user1Username, user2Username).forEach { username ->
                    val createUserRequest = """
                        {
                            "username": "$username",
                            "password": "$password",
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
            }

            test("user1 should create their own app group") {
                val createGroupRequest = """
                    {
                        "name": "User1 Group"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(user1Username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupRequest)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                user1GroupId = response.get("group").get("id").asText()
            }

            test("user2 should create their own app group") {
                val createGroupRequest = """
                    {
                        "name": "User2 Group"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(user2Username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupRequest)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                user2GroupId = response.get("group").get("id").asText()
            }

            test("user1 should only see their own groups") {
                val result = mockMvc.perform(
                    get("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(user1Username))
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("groups")
                groups shouldHaveSize 1
                groups[0].get("name").asText() shouldBe "User1 Group"
                groups[0].get("id").asText() shouldBe user1GroupId
            }

            test("user2 should only see their own groups") {
                val result = mockMvc.perform(
                    get("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(user2Username))
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                val groups = response.get("groups")
                groups shouldHaveSize 1
                groups[0].get("name").asText() shouldBe "User2 Group"
                groups[0].get("id").asText() shouldBe user2GroupId
            }

            test("verify groups are isolated in database") {
                val user1Ref = usersRepository.get(user1Username)!!
                val user1AppGroupNames = appGroupRepository.getAll(user1Ref)
                    .map { appGroupRepository.fetchDetails(it) }
                    .map { it.name }
                val user2Ref = usersRepository.get(user2Username)!!
                val user2AppGroupNames = appGroupRepository.getAll(user2Ref)
                    .map { appGroupRepository.fetchDetails(it) }
                    .map { it.name }

                user1AppGroupNames shouldHaveSize 1
                user2AppGroupNames shouldHaveSize 1
                user1AppGroupNames shouldContain  "User1 Group"
                user2AppGroupNames shouldContain  "User2 Group"
            }

            test("cleanup - delete test users") {
                listOf(user1Username, user2Username).forEach { username ->
                    usersRepository.delete(usersRepository.get(username)!!)
                }
            }
        }

        context("Edge cases - app group operations") {
            test("should handle creating app group with empty name") {
                val createGroupRequest = """
                    {
                        "name": ""
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupRequest)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                // Verify the group was created with empty name
                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("group").get("name").asText() shouldBe ""
            }

            test("should handle creating app group with very long name") {
                val longName = "A".repeat(500)
                val createGroupRequest = """
                    {
                        "name": "$longName"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupRequest)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("group").get("name").asText() shouldBe longName
            }

            test("should handle creating app group with special characters") {
                val specialName = "Test!@#\$%^&*()_+-={}[]|:;<>?,./~`"
                val createGroupRequest = """
                    {
                        "name": "$specialName"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupRequest)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                response.get("group").get("name").asText() shouldBe specialName
            }

            test("should handle creating app group with unicode characters") {
                val unicodeName = "游戏 🎮 Игры"
                val createGroupRequest = """
                    {
                        "name": "$unicodeName"
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/bff/app-groups")
                        .with(SecurityMockMvcRequestPostProcessors.user(testUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupRequest)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val response = objectMapper.readTree(result.response.getContentAsString(Charsets.UTF_8))
                response.get("group").get("name").asText() shouldBe unicodeName
            }
        }
    }
}

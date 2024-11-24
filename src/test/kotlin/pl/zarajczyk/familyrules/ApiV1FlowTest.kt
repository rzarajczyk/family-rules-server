package pl.zarajczyk.familyrules

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON
import pl.zarajczyk.familyrules.shared.DbConnector
import pl.zarajczyk.familyrules.shared.DbConnector.DeviceStates
import pl.zarajczyk.familyrules.shared.DbConnector.Instances
import pl.zarajczyk.familyrules.shared.InstanceId
import pl.zarajczyk.familyrules.shared.today
import java.util.*

class ApiV1FlowTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val v1Client = V1Client()
    private val db = TestDbClient()

    @Test
    fun `should register new instance`() {
        // when
        val response = v1Client.registerInstance(name = "testInstance", type = "TEST_CLIENT")

        // then
        response["status"] shouldBe "SUCCESS"
        response["instanceId"] shouldMatchRegex UUID_REGEX
        response["token"] shouldMatchRegex UUID_REGEX

        // and
        val instanceId = response["instanceId"].toUUID()
        db.countInstances(instanceId) shouldBe 1

        // and
        val instance = db.findInstance(instanceId)
        instance[Instances.instanceName] shouldBe "testInstance"
        instance[Instances.clientType] shouldBe "TEST_CLIENT"
    }

    @Test
    fun `should not register new instance if invalid password`() {
        // given
        val initialCount = db.countInstances()

        // when
        val response = v1Client.registerInstance(password = "invalid")

        // then
        response["status"] shouldBe "INVALID_PASSWORD"
        response["instanceId"] shouldBe null
        response["token"] shouldBe null

        // and
        db.countInstances() shouldBe initialCount
    }

    @Test
    fun `should not register new instance if instance with this name already exists`() {
        // given
        v1Client.registerInstance(name = "testInstanceX")
        val initialCount = db.countInstances()

        // when
        val response = v1Client.registerInstance(name = "testInstanceX")

        // then
        response["status"] shouldBe "INSTANCE_ALREADY_EXISTS"
        response["instanceId"] shouldBe null
        response["token"] shouldBe null

        // and
        db.countInstances() shouldBe initialCount
    }


    @Test
    fun `should send launch request`() {
        // given
        val i = v1Client.registerInstance().getInstanceSettings()

        // when
        v1Client.launch(
            instance = i,
            version = "v5",
            timezoneOffset = 45,
            availableStates = mapOf(
                "s1" to "State1",
                "s2" to "State2"
            )
        )

        // then
        val instance = db.findInstance(i.instanceId)
        instance[Instances.clientVersion] shouldBe "v5"
        instance[Instances.clientTimezoneOffsetSeconds] shouldBe 45

        // and
        db.findStates(i.instanceId) shouldBe listOf("s1", "s2")
    }

    @Test
    fun `should send report request`() {
        // given
        val i = v1Client.registerInstance().getInstanceSettings()

        // when
        v1Client.report(
            instance = i,
            screenTime = 37,
            applications = mapOf(
                "a1" to 456,
                "a2" to 567
            )
        )

        // then
        val screenTimes = db.findScreenTimesToday(i.instanceId)
        screenTimes[DbConnector.TOTAL_TIME] shouldBe 37
        screenTimes["a1"] shouldBe 456
        screenTimes["a2"] shouldBe 567
    }


    @Test
    fun `should send report request twice`() {
        // given
        val i = v1Client.registerInstance().getInstanceSettings()
        v1Client.report(
            instance = i,
            screenTime = 37,
            applications = mapOf(
                "a1" to 456,
                "a2" to 567
            )
        )

        // when
        v1Client.report(
            instance = i,
            screenTime = 145,
            applications = mapOf(
                "a1" to 12_000,
                "a2" to 67890
            )
        )

        // then
        val screenTimes = db.findScreenTimesToday(i.instanceId)
        screenTimes[DbConnector.TOTAL_TIME] shouldBe 145
        screenTimes["a1"] shouldBe 12_000
        screenTimes["a2"] shouldBe 67890
    }

    @Test
    fun `should recive report response`() {
        // given
        val i = v1Client.registerInstance().getInstanceSettings()

        // when
        val response = v1Client.report(
            instance = i,
            screenTime = 37,
            applications = mapOf(
                "a1" to 456,
                "a2" to 567
            )
        )

        // then
        response["deviceState"] shouldBe "ACTIVE"
        response["deviceStateCountdown"] shouldBe 0
    }

    // ==================================================================================

    inner class TestDbClient {

        fun findInstance(instanceId: InstanceId) = Instances
            .selectAll()
            .where { Instances.instanceId eq instanceId }
            .first()

        fun findStates(instanceId: InstanceId) = DeviceStates
            .selectAll()
            .where { DeviceStates.instanceId eq instanceId }
            .map { it[DeviceStates.deviceState] }

        fun countInstances() = Instances.selectAll().count()
        fun countInstances(name: UUID) =
            Instances.selectAll().where { Instances.instanceId eq name }.count()

        fun findScreenTimesToday(instanceId: InstanceId) = DbConnector.ScreenTimes.selectAll()
            .where { (DbConnector.ScreenTimes.instanceId eq instanceId) and (DbConnector.ScreenTimes.day eq today()) }
            .associate { row -> row[DbConnector.ScreenTimes.app] to row[DbConnector.ScreenTimes.screenTimeSeconds] }

    }

    companion object {
        val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }

    private inner class V1Client {

        fun registerInstance(
            name: String = "testInstance" + Random().nextInt(),
            type: String = "TEST_CLIENT",
            user: String = user(),
            password: String = password()
        ): Map<*, *> {
            val request = mapOf(
                "instanceName" to name,
                "clientType" to type
            )
            val headers = HttpHeaders().also {
                it.contentType = APPLICATION_JSON
                it.setBasicAuth(user, password)
            }
            val entity = HttpEntity(request, headers)
            val response = restTemplate.postForEntity(
                "/api/v1/register-instance", entity, Map::class.java
            )


            response.statusCode shouldBe HttpStatus.OK
            response.body shouldNotBe null
            return response.body!!
        }

        fun launch(
            instance: InstanceSettings,
            version: String = "v1",
            timezoneOffset: Int = 0,
            availableStates: Map<String, String> = emptyMap()
        ) {
            val request = mapOf(
                "instanceId" to instance.instanceId.toString(),
                "version" to version,
                "timezoneOffsetSeconds" to timezoneOffset,
                "availableStates" to availableStates.map {
                    mapOf(
                        "deviceState" to it.key,
                        "title" to it.value,
                        "icon" to null,
                        "description" to null
                    )
                }
            )
            val headers = HttpHeaders().also {
                it.contentType = APPLICATION_JSON
                it.setBasicAuth(user(), instance.token)
            }
            val entity = HttpEntity(request, headers)
            val response = restTemplate.postForEntity(
                "/api/v1/launch", entity, Map::class.java
            )

            response.statusCode shouldBe HttpStatus.OK
        }

        fun report(
            instance: InstanceSettings,
            screenTime: Long = 0,
            applications: Map<String, Long> = emptyMap()
        ): Map<*, *> {
            val request = mapOf(
                "instanceId" to instance.instanceId.toString(),
                "screenTime" to screenTime,
                "applications" to applications
            )
            val headers = HttpHeaders().also {
                it.contentType = APPLICATION_JSON
                it.setBasicAuth(user(), instance.token)
            }
            val entity = HttpEntity(request, headers)
            val response = restTemplate.postForEntity(
                "/api/v1/report", entity, Map::class.java
            )

            response.statusCode shouldBe HttpStatus.OK
            response.body shouldNotBe null
            return response.body!!
        }
    }

    private fun Map<*, *>.getInstanceSettings(): InstanceSettings = InstanceSettings(
        instanceId = this["instanceId"].toUUID(),
        token = this["token"].toString()
    )

    private data class InstanceSettings(
        val instanceId: UUID,
        val token: String
    )

    private fun Any?.toUUID() = UUID.fromString(this?.toString())
    private infix fun Any?.shouldMatchRegex(regex: Regex) = this!!.toString().shouldMatch(regex)


}
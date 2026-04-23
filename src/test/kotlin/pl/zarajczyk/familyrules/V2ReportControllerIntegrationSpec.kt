package pl.zarajczyk.familyrules

import com.google.cloud.firestore.Firestore
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import kotlinx.datetime.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.minus
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
class V2ReportControllerIntegrationSpec : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var usersRepository: UsersRepository

    @Autowired
    private lateinit var devicesService: DevicesService

    @Autowired
    private lateinit var devicesRepository: DevicesRepository

    @Autowired
    private lateinit var firestore: Firestore

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
        val username = "report-user-${System.currentTimeMillis()}"
        val password = "report-pass"
        val deviceName = "Report Test Device"
        val clientType = "TEST"

        lateinit var deviceId: UUID
        lateinit var token: String

        beforeSpec {
            usersRepository.createUser(username, password.sha256(), AccessLevel.PARENT)
        }

        beforeTest {
            val device = devicesService.setupNewDevice(username, "$deviceName-${System.nanoTime()}", clientType)
            deviceId = device.deviceId
            token = device.token
        }

        afterTest {
            devicesRepository.get(deviceId)?.also { devicesRepository.delete(it) }
        }

        afterSpec {
            usersRepository.get(username)?.also { usersRepository.delete(it) }
        }

        test("should accept report, persist screenTime and return deviceState") {
            val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            val reportBody = """
                {
                  "screenTime": 900,
                  "applications": {
                    "com.example.app1": 600,
                    "com.example.app2": 300
                  }
                }
            """.trimIndent()

            val result = mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reportBody)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.deviceState").exists())
                .andReturn()

            // Verify database side-effects
            val deviceRef = devicesRepository.get(deviceId)!!
            val today = today()
            val screenTimes = devicesRepository.getScreenReport(deviceRef, today)
            screenTimes.shouldNotBeNull()
            screenTimes.screenTimeSeconds shouldBe 900L
            screenTimes.applicationsSeconds["com.example.app1"] shouldBe 600L
            screenTimes.applicationsSeconds["com.example.app2"] shouldBe 300L
            screenTimes.updatedAt shouldNotBe null
            val onlinePeriods = screenTimes.onlinePeriods
            onlinePeriods.isNotEmpty() shouldBe true
            val expectedBucket = histogramBucketFor(screenTimes.updatedAt)
            onlinePeriods shouldContain expectedBucket

            val bucketData = getAppBucketData(firestore, deviceId, today, expectedBucket)
            bucketData["com.example.app1"] shouldBe 600L
            bucketData["com.example.app2"] shouldBe 300L
        }

        test("should track lastUpdatedApps for initially reported apps") {
            val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            val reportBody = """
                {
                  "screenTime": 1000,
                  "applications": {
                    "com.example.app1": 500,
                    "com.example.app2": 500
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

            // Verify lastUpdatedApps contains both apps (first report)
            val deviceRef = devicesRepository.get(deviceId)!!
            val today = today()
            val screenTimes = devicesRepository.getScreenReport(deviceRef, today)
            screenTimes.shouldNotBeNull()
            screenTimes.lastUpdatedApps.size shouldBe 2
            screenTimes.lastUpdatedApps shouldBe setOf("com.example.app1", "com.example.app2")
        }

        test("should track lastUpdatedApps only for apps with increased usage") {
            val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            
            // First report
            val firstReport = """
                {
                  "screenTime": 1000,
                  "applications": {
                    "com.example.app1": 500,
                    "com.example.app2": 300,
                    "com.example.app3": 200
                  }
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(firstReport)
            )
                .andExpect(status().isOk)

            // Second report - only app1 and app3 increased
            val secondReport = """
                {
                  "screenTime": 1500,
                  "applications": {
                    "com.example.app1": 700,
                    "com.example.app2": 300,
                    "com.example.app3": 500
                  }
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(secondReport)
            )
                .andExpect(status().isOk)

            // Verify lastUpdatedApps contains only apps with increased usage
            val deviceRef = devicesRepository.get(deviceId)!!
            val today = today()
            val screenTimes = devicesRepository.getScreenReport(deviceRef, today)
            screenTimes.shouldNotBeNull()
            screenTimes.lastUpdatedApps.size shouldBe 2
            screenTimes.lastUpdatedApps shouldBe setOf("com.example.app1", "com.example.app3")
            // app2 should not be in lastUpdatedApps as its usage didn't change
        }

        test("should handle new apps appearing in subsequent reports") {
            val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            
            // First report
            val firstReport = """
                {
                  "screenTime": 500,
                  "applications": {
                    "com.example.app1": 500
                  }
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(firstReport)
            )
                .andExpect(status().isOk)

            // Second report - app2 appears for the first time
            val secondReport = """
                {
                  "screenTime": 800,
                  "applications": {
                    "com.example.app1": 500,
                    "com.example.app2": 300
                  }
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(secondReport)
            )
                .andExpect(status().isOk)

            // Verify lastUpdatedApps contains only the new app
            val deviceRef = devicesRepository.get(deviceId)!!
            val today = today()
            val screenTimes = devicesRepository.getScreenReport(deviceRef, today)
            screenTimes.shouldNotBeNull()
            screenTimes.lastUpdatedApps.size shouldBe 1
            screenTimes.lastUpdatedApps shouldBe setOf("com.example.app2")
        }

        test("should have empty lastUpdatedApps when no apps usage increased") {
            val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            
            // First report
            val firstReport = """
                {
                  "screenTime": 1000,
                  "applications": {
                    "com.example.app1": 600,
                    "com.example.app2": 400
                  }
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(firstReport)
            )
                .andExpect(status().isOk)

            // Second report - same usage (e.g., device idle)
            val secondReport = """
                {
                  "screenTime": 1000,
                  "applications": {
                    "com.example.app1": 600,
                    "com.example.app2": 400
                  }
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(secondReport)
            )
                .andExpect(status().isOk)

            // Verify lastUpdatedApps is empty
            val deviceRef = devicesRepository.get(deviceId)!!
            val today = today()
            val screenTimes = devicesRepository.getScreenReport(deviceRef, today)
            screenTimes.shouldNotBeNull()
            screenTimes.lastUpdatedApps.size shouldBe 0
            screenTimes.lastUpdatedApps shouldBe emptySet()
        }

        test("should accumulate per-app bucket deltas from cached current totals") {
            val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())

            val firstReport = """
                {
                  "screenTime": 1000,
                  "applications": {
                    "com.example.app1": 500,
                    "com.example.app2": 300,
                    "com.example.app3": 200
                  }
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(firstReport)
            )
                .andExpect(status().isOk)

            val secondReport = """
                {
                  "screenTime": 1500,
                  "applications": {
                    "com.example.app1": 700,
                    "com.example.app2": 300,
                    "com.example.app3": 500
                  }
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(secondReport)
            )
                .andExpect(status().isOk)

            val bucketTotals = getAllAppBucketTotals(firestore, deviceId, today())
            bucketTotals.keys shouldContainAll setOf("com.example.app1", "com.example.app2", "com.example.app3")
            bucketTotals["com.example.app1"] shouldBe 700L
            bucketTotals["com.example.app2"] shouldBe 300L
            bucketTotals["com.example.app3"] shouldBe 500L
        }

        test("should start app bucket histogram from scratch on a new day") {
            val device = devicesService.get(deviceId)
            val yesterday = today().minus(1, DateTimeUnit.DAY)
            val today = today()

            device.saveScreenTimeReport(
                yesterday,
                1000,
                mapOf(
                    "com.example.app1" to 700L,
                    "com.example.app2" to 300L,
                ),
                activeApps = null,
            )

            val yesterdayBucket = histogramBucketFor(devicesRepository.getScreenReport(devicesRepository.get(deviceId)!!, yesterday)!!.updatedAt)

            device.saveScreenTimeReport(
                today,
                200,
                mapOf(
                    "com.example.app1" to 200L,
                ),
                activeApps = null,
            )

            val todayReport = devicesRepository.getScreenReport(devicesRepository.get(deviceId)!!, today)!!
            val todayBucket = histogramBucketFor(todayReport.updatedAt)

            val yesterdayTotals = getAllAppBucketTotals(firestore, deviceId, yesterday)
            yesterdayTotals["com.example.app1"] shouldBe 700L
            yesterdayTotals["com.example.app2"] shouldBe 300L

            val todayBucketData = getAppBucketData(firestore, deviceId, today, todayBucket)
            todayBucketData shouldBe mapOf("com.example.app1" to 200L)

            val yesterdayBucketData = getAppBucketData(firestore, deviceId, yesterday, yesterdayBucket)
            yesterdayBucketData shouldBe mapOf(
                "com.example.app1" to 700L,
                "com.example.app2" to 300L,
            )
        }

        test("should return 400 on invalid JSON") {
            val apiV2Basic = Base64.getEncoder().encodeToString("$deviceId:$token".toByteArray())
            val invalidJson = "{"

            mockMvc.perform(
                post("/api/v2/report")
                    .header("Authorization", "Basic $apiV2Basic")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson)
            )
                .andExpect(status().isBadRequest)
        }

    test("should fail with 401 when Authorization header missing or malformed") {
            val reportBody = """
                { "screenTime": 10, "applications": {} }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v2/report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reportBody)
            )
                .andExpect(status().isUnauthorized)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun getAppBucketData(
    firestore: Firestore,
    deviceId: UUID,
    day: kotlinx.datetime.LocalDate,
    bucket: String,
): Map<String, Long> {
    val appBuckets = getAppBuckets(firestore, deviceId, day)
    val bucketData = appBuckets[bucket] ?: emptyMap()
    return bucketData
}

private fun getAllAppBucketTotals(
    firestore: Firestore,
    deviceId: UUID,
    day: kotlinx.datetime.LocalDate,
): Map<String, Long> {
    return getAppBuckets(firestore, deviceId, day)
        .values
        .flatMap { it.entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, values) -> values.sum() }
}

@Suppress("UNCHECKED_CAST")
private fun getAppBuckets(
    firestore: Firestore,
    deviceId: UUID,
    day: kotlinx.datetime.LocalDate,
): Map<String, Map<String, Long>> {
    val instanceDoc = firestore.collectionGroup("instances")
        .whereEqualTo("instanceId", deviceId.toString())
        .get()
        .get()
        .documents
        .first()

    val dayDoc = instanceDoc.reference
        .collection("screenTimes")
        .document(day.toString())
        .get()
        .get()

    val appBuckets = dayDoc.get("appBuckets") as? Map<String, Any?> ?: emptyMap()
    return appBuckets.mapValues { (_, rawBucketData) ->
        val bucketData = rawBucketData as? Map<String, Any?> ?: emptyMap()
        bucketData.entries.associate { (encodedAppId, value) ->
            encodedAppId.decodeAppBucketKey() to when (value) {
                is Long -> value
                is Number -> value.toLong()
                else -> 0L
            }
        }
    }
}

private fun histogramBucketFor(updatedAt: Instant): String {
    val localDateTime = updatedAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minuteBucket = (localDateTime.minute / 10) * 10
    val minute = minuteBucket.toString().padStart(2, '0')
    return "$hour:$minute"
}

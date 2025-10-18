package pl.zarajczyk.familyrules.shared

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.gui.bff.SchedulePacker
import java.util.*

@Service
class FirestoreDataRepository(
    private val firestore: Firestore,
    private val schedulePacker: SchedulePacker
) : DataRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun findUser(username: String): UserDto? {
        val doc = firestore.collection("users").document(username).get().get()
        return if (doc.exists()) {
            UserDto(
                username = doc.getString("username") ?: username,
                passwordSha256 = doc.getString("passwordSha256") ?: ""
            )
        } else null
    }

    @Throws(InvalidPassword::class)
    override fun validatePassword(username: String, password: String) {
        val user = findUser(username)
        if (user?.passwordSha256 != password.sha256()) {
            throw InvalidPassword()
        }
    }

    override fun changePassword(username: String, newPassword: String) {
        val userRef = firestore.collection("users").document(username)
        userRef.update("passwordSha256", newPassword.sha256()).get()
    }

    override fun validateInstanceToken(instanceId: InstanceId, instanceToken: String): InstanceId? {
        // Find the instance across all users
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", instanceId.toString())
            .whereEqualTo("instanceTokenSha256", instanceToken.sha256())
            .whereEqualTo("deleted", false)
            .get()
            .get()

        return if (instances.isEmpty) null else instanceId
    }

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    override fun setupNewInstance(username: String, instanceName: String, clientType: String): NewInstanceDto {
        if (instanceName.length < 3) {
            throw IllegalInstanceName(instanceName)
        }

        // Check if instance name already exists for this user
        val existingInstances = firestore.collection("users")
            .document(username)
            .collection("instances")
            .whereEqualTo("instanceName", instanceName)
            .whereEqualTo("deleted", false)
            .get()
            .get()

        if (!existingInstances.isEmpty) {
            throw InstanceAlreadyExists(instanceName)
        }

        val instanceId = UUID.randomUUID()
        val instanceToken = UUID.randomUUID().toString()

        val instanceData = mapOf(
            "instanceId" to instanceId.toString(),
            "instanceName" to instanceName,
            "instanceTokenSha256" to instanceToken.sha256(),
            "clientType" to clientType,
            "clientVersion" to "v0",
            "clientTimezoneOffsetSeconds" to 0,
            "schedule" to json.encodeToString(schedulePacker.pack(WeeklyScheduleDto.empty())),
            "deleted" to false,
            "createdAt" to Clock.System.now().toString()
        )

        firestore.collection("users")
            .document(username)
            .collection("instances")
            .document(instanceId.toString())
            .set(instanceData)
            .get()

        return NewInstanceDto(instanceId, instanceToken)
    }

    override fun findInstances(username: String): List<InstanceRef> =
        firestore.collection("users")
            .document(username)
            .collection("instances")
            .whereEqualTo("deleted", false)
            .orderBy("instanceName")
            .get()
            .get()
            .documents
            .map { FirestoreInstanceRef(it) }

    override fun findInstance(instanceId: InstanceId): InstanceRef? =
        firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", instanceId.toString())
            .whereEqualTo("deleted", false)
            .get()
            .get()
            ?.documents
            ?.firstOrNull()
            ?.let { FirestoreInstanceRef(it) }

    override fun getInstance(instanceRef: InstanceRef): InstanceDto {
        val doc = (instanceRef as FirestoreInstanceRef).document

        return InstanceDto(
            id = UUID.fromString(doc.getString("instanceId") ?: ""),
            name = doc.getString("instanceName") ?: "",
            forcedDeviceState = doc.getString("forcedDeviceState"),
            clientVersion = doc.getString("clientVersion") ?: "",
            clientType = doc.getString("clientType") ?: "",
            schedule = try {
                json.decodeFromString<WeeklyScheduleDto>(doc.getString("schedule") ?: "{}")
                    .let { schedulePacker.unpack(it) }
            } catch (e: Exception) {
                WeeklyScheduleDto.empty()
            },
            iconData = doc.getString("iconData"),
            iconType = doc.getString("iconType"),
            clientTimezoneOffsetSeconds = doc.getLong("clientTimezoneOffsetSeconds")?.toInt() ?: 0
        )
    }

    override fun updateInstance(instance: InstanceRef, update: UpdateInstanceDto) {
        val doc = (instance as FirestoreInstanceRef).document

        doc.reference.update(
            "instanceName", update.name,
            "iconData", update.iconData,
            "iconType", update.iconType
        ).get()
    }

    override fun deleteInstance(instance: InstanceRef) {
        val doc = (instance as FirestoreInstanceRef).document

        doc.reference.update("deleted", true).get()
    }

    override fun setInstanceSchedule(instance: InstanceRef, schedule: WeeklyScheduleDto) {
        val doc = (instance as FirestoreInstanceRef).document
        doc.reference.update("schedule", json.encodeToString(schedulePacker.pack(schedule))).get()
    }

    override fun setForcedInstanceState(instance: InstanceRef, state: DeviceState?) {
        val doc = (instance as FirestoreInstanceRef).document
        doc.reference.update("forcedDeviceState", state).get()
    }

    override fun updateClientInformation(instance: InstanceRef, version: String, timezoneOffsetSeconds: Int, knownApps: Map<String, AppDto>) {
        val doc = (instance as FirestoreInstanceRef).document

        val knownAppsJson = json.encodeToString(
            knownApps.mapValues {
                FirestoreKnownApp(
                    appName = it.value.appName,
                    iconBase64 = it.value.iconBase64Png)
            }
        )

        doc.reference.update(
            "clientVersion", version,
            "clientTimezoneOffsetSeconds", timezoneOffsetSeconds,
            "knownApps", knownAppsJson
        ).get()
    }

    override fun getAvailableDeviceStates(instance: InstanceRef): List<DescriptiveDeviceStateDto> {
        val instanceDoc = (instance as FirestoreInstanceRef).document

        val deviceStates = instanceDoc.reference
            .collection("deviceStates")
            .orderBy("order")
            .get()
            .get()

        val states = deviceStates.documents.map { doc ->
            DescriptiveDeviceStateDto(
                deviceState = doc.getString("deviceState") ?: "",
                title = doc.getString("title") ?: "",
                icon = doc.getString("icon"),
                description = doc.getString("description")
            )
        }

        return states.ensureActiveIsPresent()
    }

    override fun updateAvailableDeviceStates(instance: InstanceRef, states: List<DescriptiveDeviceStateDto>) {
        val instanceDoc = (instance as FirestoreInstanceRef).document

        val batch = firestore.batch()

        // Clear existing device states
        val existingStates = instanceDoc.reference
            .collection("deviceStates")
            .get()
            .get()

        existingStates.documents.forEach { doc ->
            batch.delete(doc.reference)
        }

        // Add new device states
        states.forEachIndexed { index, state ->
            val stateRef = instanceDoc.reference
                .collection("deviceStates")
                .document(state.deviceState)

            batch.set(
                stateRef, mapOf(
                    "deviceState" to state.deviceState,
                    "title" to state.title,
                    "icon" to state.icon,
                    "description" to state.description,
                    "order" to index
                )
            )
        }

        batch.commit().get()
    }

    override fun saveReport(
        instanceRef: InstanceRef,
        day: LocalDate,
        screenTimeSeconds: Long,
        applicationsSeconds: Map<String, Long>
    ) {
        val instanceDoc = (instanceRef as FirestoreInstanceRef).document

        val applicationTimesJson = json.encodeToString(applicationsSeconds)

        // Store as a single field in the day document
        val screenTimeRef = instanceDoc.reference
            .collection("screenTimes")
            .document(day.toString())

        screenTimeRef.set(
            mapOf(
                "screenTime" to screenTimeSeconds,
                "applicationTimes" to applicationTimesJson,
                "updatedAt" to Clock.System.now().toString()
            )
        ).get()
    }

    override fun getScreenTimes(instance: InstanceRef, day: LocalDate): ScreenTimeDto {
        val instanceDoc = (instance as FirestoreInstanceRef).document

        val dayDoc = instanceDoc.reference
            .collection("screenTimes")
            .document(day.toString())
            .get()
            .get()

        if (!dayDoc.exists()) return emptyScreenTime()

        val totalSeconds = dayDoc.getLong("screenTime") ?: throw RuntimeException("Missing field ≪screenTime≫")
        val applicationTimesJson = dayDoc.getString("applicationTimes") ?: throw RuntimeException("Missing field ≪applicationTimes≫")
        val updatedAt = dayDoc.getString("updatedAt")?.let { Instant.parse(it) } ?: throw RuntimeException("Missing field ≪updatedAt≫")
        try {
            val applicationTimes = json.decodeFromString<Map<String, Long>>(applicationTimesJson)
            return ScreenTimeDto(totalSeconds, applicationTimes, updatedAt)
        } catch (e: Exception) {
            throw RuntimeException("JSON decoding error: $applicationTimesJson", e)
        }
    }

    fun emptyScreenTime() = ScreenTimeDto(0, emptyMap(), Instant.DISTANT_PAST)

    private fun List<DescriptiveDeviceStateDto>.ensureActiveIsPresent(): List<DescriptiveDeviceStateDto> {
        return if (this.find { it.deviceState == DEFAULT_STATE } == null) {
            this + DescriptiveDeviceStateDto(
                deviceState = DEFAULT_STATE,
                title = "Active",
                icon = null,
                description = null
            )
        } else {
            this
        }
    }
}

class FirestoreKnownApp(
    val appName: String,
    val iconBase64: String?
)

class FirestoreInstanceRef(val document: QueryDocumentSnapshot) : InstanceRef
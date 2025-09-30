package pl.zarajczyk.familyrules.shared

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.WriteBatch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit.Companion.DAY
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.gui.bff.SchedulePacker
import java.util.*
import kotlin.Throws

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

    override fun getInstances(username: String): List<InstanceDto> {
        val instances = firestore.collection("users")
            .document(username)
            .collection("instances")
            .whereEqualTo("deleted", false)
            .orderBy("instanceName")
            .get()
            .get()

        return instances.documents.map { doc ->
            InstanceDto(
                id = UUID.fromString(doc.getString("instanceId") ?: ""),
                name = doc.getString("instanceName") ?: "",
                forcedDeviceState = doc.getString("forcedDeviceState"),
                clientVersion = doc.getString("clientVersion") ?: "",
                clientType = doc.getString("clientType") ?: "",
                schedule = try {
                    json.decodeFromString<WeeklyScheduleDto>(doc.getString("schedule") ?: "{}").let { schedulePacker.unpack(it) }
                } catch (e: Exception) {
                    WeeklyScheduleDto.empty()
                },
                iconData = doc.getString("iconData"),
                iconType = doc.getString("iconType"),
                clientTimezoneOffsetSeconds = doc.getLong("clientTimezoneOffsetSeconds")?.toInt() ?: 0
            )
        }
    }

    override fun getInstance(instanceId: InstanceId): InstanceDto? {
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", instanceId.toString())
            .whereEqualTo("deleted", false)
            .get()
            .get()

        return instances.documents.firstOrNull()?.let { doc ->
            InstanceDto(
                id = UUID.fromString(doc.getString("instanceId") ?: ""),
                name = doc.getString("instanceName") ?: "",
                forcedDeviceState = doc.getString("forcedDeviceState"),
                clientVersion = doc.getString("clientVersion") ?: "",
                clientType = doc.getString("clientType") ?: "",
                schedule = try {
                    json.decodeFromString<WeeklyScheduleDto>(doc.getString("schedule") ?: "{}").let { schedulePacker.unpack(it) }
                } catch (e: Exception) {
                    WeeklyScheduleDto.empty()
                },
                iconData = doc.getString("iconData"),
                iconType = doc.getString("iconType"),
                clientTimezoneOffsetSeconds = doc.getLong("clientTimezoneOffsetSeconds")?.toInt() ?: 0
            )
        }
    }

    override fun updateInstance(instanceId: InstanceId, update: UpdateInstanceDto) {
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", instanceId.toString())
            .get()
            .get()

        instances.documents.forEach { doc ->
            doc.reference.update(
                "instanceName", update.name,
                "iconData", update.iconData,
                "iconType", update.iconType
            ).get()
        }
    }

    override fun deleteInstance(instanceId: InstanceId) {
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", instanceId.toString())
            .get()
            .get()

        instances.documents.forEach { doc ->
            doc.reference.update("deleted", true).get()
        }
    }

    override fun setInstanceSchedule(id: InstanceId, schedule: WeeklyScheduleDto) {
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", id.toString())
            .get()
            .get()

        instances.documents.forEach { doc ->
            doc.reference.update("schedule", json.encodeToString(schedulePacker.pack(schedule))).get()
        }
    }

    override fun setForcedInstanceState(id: InstanceId, state: DeviceState?) {
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", id.toString())
            .get()
            .get()

        instances.documents.forEach { doc ->
            doc.reference.update("forcedDeviceState", state).get()
        }
    }

    override fun updateClientInformation(id: InstanceId, version: String, timezoneOffsetSeconds: Int) {
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", id.toString())
            .get()
            .get()

        instances.documents.forEach { doc ->
            doc.reference.update(
                "clientVersion", version,
                "clientTimezoneOffsetSeconds", timezoneOffsetSeconds
            ).get()
        }
    }

    override fun getAvailableDeviceStates(id: InstanceId): List<DescriptiveDeviceStateDto> {
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", id.toString())
            .get()
            .get()

        if (instances.isEmpty) return emptyList()

        val instanceDoc = instances.documents.first()
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

    override fun updateAvailableDeviceStates(id: InstanceId, states: List<DescriptiveDeviceStateDto>) {
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", id.toString())
            .get()
            .get()

        if (instances.isEmpty) return

        val instanceDoc = instances.documents.first()
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

            batch.set(stateRef, mapOf(
                "deviceState" to state.deviceState,
                "title" to state.title,
                "icon" to state.icon,
                "description" to state.description,
                "order" to index
            ))
        }

        batch.commit().get()
    }

    override fun saveReport(instanceId: InstanceId, day: LocalDate, screenTimeSeconds: Long, applicationsSeconds: Map<String, Long>) {
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", instanceId.toString())
            .get()
            .get()

        if (instances.isEmpty) return

        val instanceDoc = instances.documents.first()
        val total = applicationsSeconds + mapOf(TOTAL_TIME to screenTimeSeconds)
        val batch = firestore.batch()

        total.entries.forEach { (app, seconds) ->
            val screenTimeRef = instanceDoc.reference
                .collection("screenTimes")
                .document("${day}-$app")

            batch.set(screenTimeRef, mapOf(
                "app" to app,
                "screenTimeSeconds" to seconds,
                "updatedAt" to Clock.System.now().toString()
            ))
        }

        batch.commit().get()
    }

    override fun getScreenTimes(id: InstanceId, day: LocalDate): Map<String, ScreenTimeDto> {
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", id.toString())
            .get()
            .get()

        if (instances.isEmpty) return emptyMap()

        val instanceDoc = instances.documents.first()
        val nextDay = day.plus(1, DAY)
        val screenTimes = instanceDoc.reference
            .collection("screenTimes")
            .whereGreaterThanOrEqualTo("app", day.toString())
            .whereLessThan("app", nextDay.toString())
            .get()
            .get()

        return screenTimes.documents.associate { doc ->
            val app = doc.getString("app") ?: ""
            val seconds = doc.getLong("screenTimeSeconds") ?: 0L
            val updatedAt = doc.getString("updatedAt")?.let { Instant.parse(it) } ?: Clock.System.now()
            app to ScreenTimeDto(seconds, updatedAt)
        }
    }

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

    companion object {
        const val TOTAL_TIME = "## screentime ##"
    }
}

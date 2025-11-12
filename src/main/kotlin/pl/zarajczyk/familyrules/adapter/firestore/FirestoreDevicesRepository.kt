package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.gui.bff.SchedulePacker
import java.util.*

@Service
class FirestoreDevicesRepository(
    private val firestore: Firestore,
    private val schedulePacker: SchedulePacker
) : DevicesRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun validateDeviceToken(deviceId: InstanceId, deviceToken: String): InstanceId? {
        // Find the instance across all users
        val instances = firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", deviceId.toString())
            .whereEqualTo("instanceTokenSha256", deviceToken.sha256())
            .whereEqualTo("deleted", false)
            .get()
            .get()

        return if (instances.isEmpty) null else deviceId
    }

    override fun getByName(user: UserRef, deviceName: String): DeviceRef? {
        return (user as FirestoreUserRef).doc
            .collection("instances")
            .whereEqualTo("instanceName", deviceName)
            .whereEqualTo("deleted", false)
            .get()
            .get()
            ?.documents
            ?.firstOrNull()
            ?.let { FirestoreDeviceRef(it) }
    }

    override fun createNewDevice(
        user: UserRef,
        instanceName: String,
        clientType: String
    ): NewDeviceDto {
        val instanceId = UUID.randomUUID()
        val instanceToken = UUID.randomUUID().toString()

        val instanceData = mapOf(
            "instanceId" to instanceId.toString(),
            "instanceName" to instanceName,
            "instanceTokenSha256" to instanceToken.sha256(),
            "clientType" to clientType,
            "clientVersion" to "v0",
            "clientTimezoneOffsetSeconds" to 0,
            "schedule" to json.encodeToString(schedulePacker.pack(WeeklyScheduleDto.Companion.empty())),
            "deleted" to false,
            "createdAt" to Clock.System.now().toString()
        )

        (user as FirestoreUserRef).doc
            .collection("instances")
            .document(instanceId.toString())
            .set(instanceData)
            .get()

        return NewDeviceDto(instanceId, instanceToken)
    }

    // TODO: UserRef
    override fun getAll(username: String): List<InstanceRef> =
        firestore.collection("users")
            .document(username)
            .collection("instances")
            .whereEqualTo("deleted", false)
            .orderBy("instanceName")
            .get()
            .get()
            .documents
            .map { FirestoreDeviceRef(it) }

    override fun get(instanceId: InstanceId): InstanceRef? =
        firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", instanceId.toString())
            .whereEqualTo("deleted", false)
            .get()
            .get()
            ?.documents
            ?.firstOrNull()
            ?.let { FirestoreDeviceRef(it) }

    override fun fetchBasicData(instanceRef: InstanceRef): BasicDeviceDto {
        val doc = (instanceRef as FirestoreDeviceRef).document
        return BasicDeviceDto(
            id = UUID.fromString(doc.getString("instanceId") ?: ""),
            name = doc.getString("instanceName") ?: "",
        )
    }

    override fun fetchDetails(instanceRef: InstanceRef): DeviceDto {
        val doc = (instanceRef as FirestoreDeviceRef).document

        return DeviceDto(
            id = UUID.fromString(doc.getString("instanceId") ?: ""),
            name = doc.getString("instanceName") ?: "",
            forcedDeviceState = doc.getString("forcedDeviceState")?.let {
                DeviceStateDto(it, doc.getString("forcedDeviceStateExtra"))
            },
            clientVersion = doc.getString("clientVersion") ?: "",
            clientType = doc.getString("clientType") ?: "",
            schedule = try {
                json.decodeFromString<WeeklyScheduleDto>(doc.getString("schedule") ?: "{}")
                    .let { schedulePacker.unpack(it) }
            } catch (e: Exception) {
                WeeklyScheduleDto.Companion.empty()
            },
            iconData = doc.getString("iconData"),
            iconType = doc.getString("iconType"),
            clientTimezoneOffsetSeconds = doc.getLong("clientTimezoneOffsetSeconds")?.toInt() ?: 0,
            reportIntervalSeconds = doc.getLong("reportIntervalSeconds")?.toInt(),
            knownApps = try {
                json.decodeFromString<Map<String, FirestoreKnownApp>>(doc.getString("knownApps") ?: "{}")
                    .mapValues { AppDto(it.value.appName, it.value.iconBase64) }
            } catch (e: Exception) {
                emptyMap()
            },
            associatedAppGroupId = doc.getString("associatedAppGroupId")
        )
    }

    override fun updateInstance(instance: InstanceRef, update: UpdateInstanceDto) {
        val doc = (instance as FirestoreDeviceRef).document

        doc.reference.update(
            "instanceName", update.name,
            "iconData", update.iconData,
            "iconType", update.iconType
        ).get()
    }

    override fun delete(instance: InstanceRef) {
        val doc = (instance as FirestoreDeviceRef).document

        doc.reference.update("deleted", true).get()
    }

    override fun setInstanceSchedule(instance: InstanceRef, schedule: WeeklyScheduleDto) {
        val doc = (instance as FirestoreDeviceRef).document
        doc.reference.update("schedule", json.encodeToString(schedulePacker.pack(schedule))).get()
    }

    override fun setForcedInstanceState(instance: InstanceRef, state: DeviceStateDto?) {
        val doc = (instance as FirestoreDeviceRef).document
        doc.reference.update(
            "forcedDeviceState", state?.deviceState,
            "forcedDeviceStateExtra", state?.extra
        ).get()
    }

    override fun updateClientInformation(instance: InstanceRef, clientInfo: ClientInfoDto) {
        val doc = (instance as FirestoreDeviceRef).document

        val knownAppsJson = json.encodeToString(
            clientInfo.knownApps.mapValues {
                FirestoreKnownApp(
                    appName = it.value.appName,
                    iconBase64 = it.value.iconBase64Png
                )
            }
        )

        val deviceStatesJson = json.encodeToString(clientInfo.states)

        // Update client information
        doc.reference.update(
            "clientVersion", clientInfo.version,
            "clientTimezoneOffsetSeconds", clientInfo.timezoneOffsetSeconds,
            "reportIntervalSeconds", clientInfo.reportIntervalSeconds,
            "knownApps", knownAppsJson,
            "deviceStates", deviceStatesJson
        ).get()
    }

    override fun setAssociatedAppGroup(instance: InstanceRef, groupId: String?) {
        val doc = (instance as FirestoreDeviceRef).document
        doc.reference.update("associatedAppGroupId", groupId).get()
    }

    override fun getAvailableDeviceStateTypes(instance: InstanceRef): List<DeviceStateTypeDto> {
        val instanceDoc = (instance as FirestoreDeviceRef).document

        val deviceStatesJson = instanceDoc.getString("deviceStates")
        return when {
            deviceStatesJson.isNullOrBlank() -> emptyList()
            else -> json.decodeFromString<List<DeviceStateTypeDto>>(deviceStatesJson)
        }.ensureActiveIsPresent()
    }

    override fun saveReport(
        instanceRef: InstanceRef,
        day: LocalDate,
        screenTimeSeconds: Long,
        applicationsSeconds: Map<String, Long>
    ) {
        val instanceDoc = (instanceRef as FirestoreDeviceRef).document

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
        val instanceDoc = (instance as FirestoreDeviceRef).document

        val dayDoc = instanceDoc.reference
            .collection("screenTimes")
            .document(day.toString())
            .get()
            .get()

        if (!dayDoc.exists()) return emptyScreenTime()

        val totalSeconds = dayDoc.getLong("screenTime") ?: throw RuntimeException("Missing field ≪screenTime≫")
        val applicationTimesJson =
            dayDoc.getString("applicationTimes") ?: throw RuntimeException("Missing field ≪applicationTimes≫")
        val updatedAt = dayDoc.getString("updatedAt")?.let { Instant.parse(it) }
            ?: throw RuntimeException("Missing field ≪updatedAt≫")
        try {
            val applicationTimes = json.decodeFromString<Map<String, Long>>(applicationTimesJson)
            return ScreenTimeDto(totalSeconds, applicationTimes, updatedAt)
        } catch (e: Exception) {
            throw RuntimeException("JSON decoding error: $applicationTimesJson", e)
        }
    }

    fun emptyScreenTime() = ScreenTimeDto(0, emptyMap(), Instant.DISTANT_PAST)


    private fun List<DeviceStateTypeDto>.ensureActiveIsPresent(): List<DeviceStateTypeDto> {
        return if (this.find { it.deviceState == DEFAULT_DEVICE_STATE } == null) {
            this + DeviceStateTypeDto(
                deviceState = DEFAULT_DEVICE_STATE,
                title = "Active",
                icon = null,
                description = null,
                arguments = emptySet()
            )
        } else {
            this
        }
    }

    override fun getOwner(deviceRef: DeviceRef): UserRef {
        val doc = (deviceRef as FirestoreDeviceRef)
            .document
            .reference
            .parent
            .parent
            ?: throw UserNotFoundException("owner for device")
        return FirestoreUserRef(doc)
    }
}

@Serializable
class FirestoreKnownApp(
    val appName: String,
    val iconBase64: String?
)

class FirestoreDeviceRef(val document: QueryDocumentSnapshot) : InstanceRef
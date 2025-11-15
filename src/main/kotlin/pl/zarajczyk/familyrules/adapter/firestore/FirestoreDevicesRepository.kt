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
import pl.zarajczyk.familyrules.domain.port.*
import pl.zarajczyk.familyrules.gui.bff.SchedulePacker
import java.util.*

@Service
class FirestoreDevicesRepository(
    private val firestore: Firestore,
    private val schedulePacker: SchedulePacker
) : DevicesRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getByName(user: UserRef, deviceName: String): DeviceRef? {
        return (user as FirestoreUserRef).doc
            .collection("instances")
            .whereEqualTo("instanceName", deviceName)
            .get()
            .get()
            ?.documents
            ?.firstOrNull()
            ?.let { FirestoreDeviceRef(it) }
    }

    override fun createDevice(user: UserRef, details: DeviceDetailsDto): DeviceRef {
        val instanceData = mapOf(
            "instanceId" to details.deviceId.toString(),
            "instanceName" to details.deviceName,
            "forcedDeviceState" to details.forcedDeviceState?.deviceState,
            "forcedDeviceStateExtra" to details.forcedDeviceState?.extra,
            "instanceTokenSha256" to details.hashedToken,
            "clientType" to details.clientType,
            "clientVersion" to details.clientVersion,
            "clientTimezoneOffsetSeconds" to details.clientTimezoneOffsetSeconds,
            "schedule" to encode(WeeklyScheduleDto.Companion.empty()),
            "iconData" to details.iconData,
            "iconType" to details.iconType,
            "knownApps" to encode(details.knownApps),
            "reportIntervalSeconds" to details.reportIntervalSeconds,
            "deviceStates" to encode(details.availableDeviceStates)
        )

        val doc = (user as FirestoreUserRef).doc
            .collection("instances")
            .document(details.deviceId.toString())

        doc.set(instanceData).get()

        return get(details.deviceId) ?: throw DeviceNotFoundException(details.deviceId)
    }

    private fun encode(schedule: WeeklyScheduleDto): String =
        json.encodeToString(schedulePacker.pack(schedule))

    private fun encode(states: List<DeviceStateTypeDto>): String =
        json.encodeToString(states)

    private fun encode(knownApps: Map<String, AppDto>): String =
        json.encodeToString(
            knownApps.mapValues {
                FirestoreKnownApp(
                    appName = it.value.appName,
                    iconBase64 = it.value.iconBase64Png
                )
            }
        )

    override fun getAll(userRef: UserRef): List<InstanceRef> =
        (userRef as FirestoreUserRef).doc
            .collection("instances")
            .orderBy("instanceName")
            .get()
            .get()
            .documents
            .map { FirestoreDeviceRef(it) }

    override fun get(id: InstanceId): InstanceRef? =
        firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", id.toString())
            .get()
            .get()
            ?.documents
            ?.firstOrNull()
            ?.let { FirestoreDeviceRef(it) }

    override fun fetchDetails(device: DeviceRef, includePasswordHash: Boolean): DeviceDetailsDto {
        val doc = (device as FirestoreDeviceRef).document

        return DeviceDetailsDto(
            deviceId = UUID.fromString(doc.getStringOrThrow("instanceId")),
            deviceName = doc.getStringOrThrow("instanceName"),
            forcedDeviceState = doc.getDeviceStateDto("forcedDeviceState", "forcedDeviceStateExtra"),
            clientVersion = doc.getStringOrThrow("clientVersion"),
            clientType = doc.getStringOrThrow("clientType"),
            schedule = doc.getSchedule("schedule"),
            clientTimezoneOffsetSeconds = doc.getLongOrThrow("clientTimezoneOffsetSeconds"),
            hashedToken = when (includePasswordHash) {
                true -> doc.getStringOrThrow("instanceTokenSha256")
                false -> ""
            },
            iconData = doc.getString("iconData"),
            iconType = doc.getString("iconType"),
            reportIntervalSeconds = doc.getLongOrThrow("reportIntervalSeconds"),
            knownApps = doc.getKnownAppsOrThrow("knownApps"),
            availableDeviceStates = doc.getAvailableDeviceStates("deviceStates")
        )
    }

    override fun update(device: DeviceRef, details: DeviceDetailsUpdateDto) {
        val instanceData = listOfNotNull(
            details.deviceId.ifPresent { "instanceId" to it.toString() },
            details.deviceName.ifPresent { "instanceName" to it },
            details.forcedDeviceState.ifPresent { "forcedDeviceState" to it?.deviceState },
            details.forcedDeviceState.ifPresent { "forcedDeviceStateExtra" to it?.extra },
            details.hashedToken.ifPresent { "instanceTokenSha256" to it },
            details.clientType.ifPresent { "clientType" to it },
            details.clientVersion.ifPresent { "clientVersion" to it },
            details.clientTimezoneOffsetSeconds.ifPresent { "clientTimezoneOffsetSeconds" to it },
            details.schedule.ifPresent { "schedule" to encode(it) },
            details.iconData.ifPresent { "iconData" to it },
            details.iconType.ifPresent { "iconType" to it },
            details.knownApps.ifPresent { "knownApps" to encode(it) },
            details.reportIntervalSeconds.ifPresent { "reportIntervalSeconds" to it },
            details.availableDeviceStates.ifPresent { "deviceStates" to encode(it) }
        ).toMap()

        val doc = (device as FirestoreDeviceRef).document

        doc.reference.update(instanceData).get()
    }

    private fun QueryDocumentSnapshot.getDeviceStateDto(fieldName: String, extraFieldName: String): DeviceStateDto? =
        getString(fieldName)?.let {
            DeviceStateDto(it, getString(extraFieldName))
        }

    private fun QueryDocumentSnapshot.getKnownAppsOrThrow(fieldName: String) =
        json.decodeFromString<Map<String, FirestoreKnownApp>>(getStringOrThrow(fieldName))
            .mapValues { AppDto(it.value.appName, it.value.iconBase64) }

    private fun QueryDocumentSnapshot.getSchedule(fieldName: String) =
        json.decodeFromString<WeeklyScheduleDto>(getString(fieldName) ?: "{}")
            .let { schedulePacker.unpack(it) }

    private fun QueryDocumentSnapshot.getAvailableDeviceStates(fieldName: String) =
        json.decodeFromString<List<DeviceStateTypeDto>>(getString(fieldName) ?: "[]").ensureActiveIsPresent()


    override fun delete(device: InstanceRef) {
        (device as FirestoreDeviceRef)
            .document
            .reference
            .delete()
            .get()
    }

    override fun saveReport(
        instance: InstanceRef,
        day: LocalDate,
        screenTimeSeconds: Long,
        applicationsSeconds: Map<String, Long>
    ) {
        val instanceDoc = (instance as FirestoreDeviceRef).document

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
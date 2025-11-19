package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.*
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
        val deviceData = mapOf(
            "instanceId" to details.deviceId.toString(),
            "instanceName" to details.deviceName,
            "forcedDeviceState" to details.forcedDeviceState?.deviceState,
            "forcedDeviceStateExtra" to details.forcedDeviceState?.extra,
            "instanceTokenSha256" to details.hashedToken,
            "clientType" to details.clientType,
            "clientVersion" to details.clientVersion,
            "clientTimezoneOffsetSeconds" to details.clientTimezoneOffsetSeconds,
            "schedule" to WeeklyScheduleDto.empty().encodeSchedule(),
            "iconData" to details.iconData,
            "iconType" to details.iconType,
            "knownApps" to details.knownApps.encodeKnownApps(),
            "reportIntervalSeconds" to details.reportIntervalSeconds,
            "deviceStates" to details.availableDeviceStates.encodeDeviceStates(),
            "appGroups" to details.appGroups.encodeAppGroups()
        )

        val doc = (user as FirestoreUserRef).doc
            .collection("instances")
            .document(details.deviceId.toString())

        doc.set(deviceData).get()

        return get(details.deviceId) ?: throw DeviceNotFoundException(details.deviceId)
    }

    private fun WeeklyScheduleDto.encodeSchedule(): String =
        json.encodeToString(schedulePacker.pack(this))

    private fun List<DeviceStateTypeDto>.encodeDeviceStates(): String =
        json.encodeToString(this)

    private fun AppGroupsDto.encodeAppGroups(): String =
        json.encodeToString(this)

    private fun Map<String, AppDto>.encodeKnownApps(): String =
        json.encodeToString(
            this.mapValues {
                FirestoreKnownApp(
                    appName = it.value.appName,
                    iconBase64 = it.value.iconBase64Png
                )
            }
        )

    override fun getAll(userRef: UserRef): List<DeviceRef> =
        (userRef as FirestoreUserRef).doc
            .collection("instances")
            .orderBy("instanceName")
            .get()
            .get()
            .documents
            .map { FirestoreDeviceRef(it) }

    override fun get(id: DeviceId): DeviceRef? =
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
            availableDeviceStates = doc.getAvailableDeviceStates("deviceStates"),
            appGroups = doc.getAppGroups("appGroups")
        )
    }

    override fun update(device: DeviceRef, details: DeviceDetailsUpdateDto) {
        val deviceData = listOfNotNull(
            details.deviceId.ifPresent { "instanceId" to it.toString() },
            details.deviceName.ifPresent { "instanceName" to it },
            details.forcedDeviceState.ifPresent { "forcedDeviceState" to it?.deviceState },
            details.forcedDeviceState.ifPresent { "forcedDeviceStateExtra" to it?.extra },
            details.hashedToken.ifPresent { "instanceTokenSha256" to it },
            details.clientType.ifPresent { "clientType" to it },
            details.clientVersion.ifPresent { "clientVersion" to it },
            details.clientTimezoneOffsetSeconds.ifPresent { "clientTimezoneOffsetSeconds" to it },
            details.schedule.ifPresent { "schedule" to it.encodeSchedule() },
            details.iconData.ifPresent { "iconData" to it },
            details.iconType.ifPresent { "iconType" to it },
            details.knownApps.ifPresent { "knownApps" to it.encodeKnownApps() },
            details.reportIntervalSeconds.ifPresent { "reportIntervalSeconds" to it },
            details.availableDeviceStates.ifPresent { "deviceStates" to it.encodeDeviceStates() },
            details.appGroups.ifPresent { "appGroups" to it.encodeAppGroups() }
        ).toMap()

        val doc = (device as FirestoreDeviceRef).document

        doc.reference.update(deviceData).get()
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

    private fun QueryDocumentSnapshot.getAppGroups(fieldName: String): AppGroupsDto =
        try {
            json.decodeFromString<AppGroupsDto>(getString(fieldName) ?: "{}")
        } catch (e: Exception) {
            AppGroupsDto.empty()
        }


    override fun delete(device: DeviceRef) {
        (device as FirestoreDeviceRef)
            .document
            .reference
            .delete()
            .get()
    }

    override fun setScreenReport(
        device: DeviceRef,
        day: LocalDate,
        screenReportDto: ScreenReportDto
    ) {
        val doc = (device as FirestoreDeviceRef).document

        val screenTimeRef = doc.reference
            .collection("screenTimes")
            .document(day.toString())

        screenTimeRef.set(
            mapOf(
                "screenTime" to screenReportDto.screenTimeSeconds,
                "applicationTimes" to screenReportDto.applicationsSeconds.encodeApplicationTimes(),
                "updatedAt" to screenReportDto.updatedAt.toString(),
                "screenTimeHistogram" to screenReportDto.screenTimeHistogram.encodeHistogram()
            )
        ).get()
    }

    private fun Map<String, Long>.encodeApplicationTimes() = json.encodeToString(this)
    private fun Map<String, Long>.encodeHistogram() = json.encodeToString(this)

    override fun getScreenReport(device: DeviceRef, day: LocalDate): ScreenReportDto? {
        val doc = (device as FirestoreDeviceRef).document

        val dayDoc = doc.reference
            .collection("screenTimes")
            .document(day.toString())
            .get()
            .get()

        if (!dayDoc.exists()) return null

        val screenTimeSeconds = dayDoc.getLongOrThrow("screenTime")
        val updatedAt = dayDoc.getStringOrThrow("updatedAt").let { Instant.parse(it) }
        val applicationTimes = dayDoc.getApplicationTimes("applicationTimes")
        val screenTimeHistogram = dayDoc.getHistogram("screenTimeHistogram")

        return ScreenReportDto(
            screenTimeSeconds = screenTimeSeconds,
            applicationsSeconds = applicationTimes,
            updatedAt = updatedAt,
            screenTimeHistogram = screenTimeHistogram
        )
    }

    private fun DocumentSnapshot.getApplicationTimes(fieldName: String): Map<String, Long> =
        json.decodeFromString(getStringOrThrow(fieldName))

    private fun DocumentSnapshot.getHistogram(fieldName: String): Map<String, Long> =
        json.decodeFromString(getString(fieldName) ?: "{}")


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

class FirestoreDeviceRef(val document: QueryDocumentSnapshot) : DeviceRef
package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.google.cloud.firestore.SetOptions
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
            ?.let { fetchDetails(it) }
    }

    override fun createDevice(user: UserRef, details: DeviceDetailsDto, tokenHash: String): DeviceRef {
        val deviceData = mapOf(
            "instanceId" to details.deviceId.toString(),
            "instanceName" to details.deviceName,
            "forcedDeviceState" to details.forcedDeviceState?.deviceState,
            "forcedDeviceStateExtra" to details.forcedDeviceState?.extra,
            "instanceTokenSha256" to tokenHash,
            "clientType" to details.clientType,
            "clientVersion" to details.clientVersion,
            "clientTimezoneOffsetSeconds" to details.clientTimezoneOffsetSeconds,
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
            .map { fetchDetails(it) }

    override fun get(id: DeviceId): DeviceRef? =
        firestore.collectionGroup("instances")
            .whereEqualTo("instanceId", id.toString())
            .get()
            .get()
            ?.documents
            ?.firstOrNull()
            ?.let { fetchDetails(it) }

    private fun fetchDetails(doc: QueryDocumentSnapshot): DeviceRef {
        val details = DeviceDetailsDto(
            deviceId = UUID.fromString(doc.getStringOrThrow("instanceId")),
            deviceName = doc.getStringOrThrow("instanceName"),
            forcedDeviceState = doc.getDeviceStateDto("forcedDeviceState", "forcedDeviceStateExtra"),
            clientVersion = doc.getStringOrThrow("clientVersion"),
            clientType = doc.getStringOrThrow("clientType"),
            clientTimezoneOffsetSeconds = doc.getLongOrThrow("clientTimezoneOffsetSeconds"),
            iconData = doc.getString("iconData"),
            iconType = doc.getString("iconType"),
            reportIntervalSeconds = doc.getLongOrThrow("reportIntervalSeconds"),
            knownApps = doc.getKnownAppsOrThrow("knownApps"),
            availableDeviceStates = doc.getAvailableDeviceStates("deviceStates"),
            appGroups = doc.getAppGroups("appGroups"),
            currentDay = doc.getString("currentDay"),
            currentScreenTime = doc.getLong("currentScreenTime"),
            currentApplicationTimes = doc.getNativeApplicationTimes("currentApplicationTimes"),
            currentUpdatedAt = doc.getString("currentUpdatedAt")?.let { Instant.parse(it) },
            currentLastUpdatedApps = doc.getNativeLastUpdatedApps("currentLastUpdatedApps"),
            currentOnlinePeriods = doc.getNativeOnlinePeriods("currentOnlinePeriods"),
        )
        val tokenHash = doc.getStringOrThrow("instanceTokenSha256")
        return FirestoreDeviceRef(doc, details, tokenHash)
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

    private fun QueryDocumentSnapshot.getAvailableDeviceStates(fieldName: String) =
        json.decodeFromString<List<DeviceStateTypeDto>>(getString(fieldName) ?: "[]").ensureActiveIsPresent()

    private fun QueryDocumentSnapshot.getAppGroups(fieldName: String): AppGroupsDto =
        try {
            json.decodeFromString<AppGroupsDto>(getString(fieldName) ?: "{}")
        } catch (_: Exception) {
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
        screenReportDto: SetScreenReportDto
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
                "lastUpdatedApps" to screenReportDto.lastUpdatedApps.encodeLastUpdatedApps(),
                "onlinePeriods" to FieldValue.arrayUnion(screenReportDto.currentOnlinePeriodBucket)
            ),
            SetOptions.merge()
        ).get()
    }

    private fun Map<String, Long>.encodeApplicationTimes() = json.encodeToString(this)
    private fun Set<String>.encodeLastUpdatedApps() = json.encodeToString(this)

    override fun setCurrentScreenReport(
        device: DeviceRef,
        day: LocalDate,
        screenReportDto: SetScreenReportDto
    ) {
        val doc = (device as FirestoreDeviceRef).document
        doc.reference.update(
            mapOf(
                "currentDay" to day.toString(),
                "currentScreenTime" to screenReportDto.screenTimeSeconds,
                "currentApplicationTimes" to screenReportDto.applicationsSeconds,
                "currentUpdatedAt" to screenReportDto.updatedAt.toString(),
                "currentLastUpdatedApps" to screenReportDto.lastUpdatedApps.toList(),
                "currentOnlinePeriods" to screenReportDto.currentOnlinePeriods.toList()
            )
        ).get()
    }

    @Suppress("UNCHECKED_CAST")
    private fun QueryDocumentSnapshot.getNativeApplicationTimes(fieldName: String): Map<String, Long>? =
        (get(fieldName) as? Map<String, Any>)?.mapValues { (_, v) ->
            when (v) {
                is Long -> v
                is Number -> v.toLong()
                else -> 0L
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun QueryDocumentSnapshot.getNativeLastUpdatedApps(fieldName: String): Set<String>? =
        (get(fieldName) as? List<String>)?.toSet()

    @Suppress("UNCHECKED_CAST")
    private fun QueryDocumentSnapshot.getNativeOnlinePeriods(fieldName: String): Set<String>? =
        (get(fieldName) as? List<String>)?.toSet()

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
        val onlinePeriods = dayDoc.getOnlinePeriods("onlinePeriods")
        val lastUpdatedApps = dayDoc.getLastUpdatedApps("lastUpdatedApps")

        return ScreenReportDto(
            screenTimeSeconds = screenTimeSeconds,
            applicationsSeconds = applicationTimes,
            updatedAt = updatedAt,
            onlinePeriods = onlinePeriods,
            lastUpdatedApps = lastUpdatedApps
        )
    }

    private fun DocumentSnapshot.getApplicationTimes(fieldName: String): Map<String, Long> =
        json.decodeFromString(getStringOrThrow(fieldName))

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.getOnlinePeriods(fieldName: String): Set<String> =
        (get(fieldName) as? List<String>)?.toSet() ?: emptySet()

    private fun DocumentSnapshot.getLastUpdatedApps(fieldName: String): Set<String> =
        json.decodeFromString(getString(fieldName) ?: "[]")


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
            ?: throw UserNotFoundException("parent is null")
        return FirestoreUsersRepository.fetch(doc) ?: throw UserNotFoundException("owner for device not found")
    }

    override fun updateOwnerLastActivity(deviceRef: DeviceRef, lastActivityMillis: Long) {
        val userDoc = (deviceRef as FirestoreDeviceRef)
            .document
            .reference
            .parent
            .parent
            ?: throw UserNotFoundException("parent is null")
        val timestamp = com.google.cloud.Timestamp.ofTimeMicroseconds(lastActivityMillis * 1000)
        userDoc.update("lastActivity", timestamp).get()
    }
}

@Serializable
class FirestoreKnownApp(
    val appName: String,
    val iconBase64: String?
)

class FirestoreDeviceRef(
    val document: QueryDocumentSnapshot,
    override val details: DeviceDetailsDto,
    override val tokenHash: String
) : DeviceRef {
    override fun getDeviceId(): DeviceId = details.deviceId
}
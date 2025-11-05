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
                passwordSha256 = doc.getString("passwordSha256") ?: "",
                accessLevel = doc.getString("accessLevel")?.let { AccessLevel.valueOf(it) } ?: AccessLevel.ADMIN
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

    override fun getAllUsers(): List<UserDto> {
        return firestore.collection("users")
            .get()
            .get()
            .documents
            .map { doc ->
                UserDto(
                    username = doc.getString("username") ?: doc.id,
                    passwordSha256 = doc.getString("passwordSha256") ?: "",
                    accessLevel = doc.getString("accessLevel")?.let { AccessLevel.valueOf(it) } ?: AccessLevel.ADMIN
                )
            }
    }

    override fun deleteUser(username: String) {
        firestore.collection("users").document(username).delete().get()
    }

    override fun createUser(username: String, password: String, accessLevel: AccessLevel) {
        val userData = mapOf(
            "username" to username,
            "passwordSha256" to password.sha256(),
            "accessLevel" to accessLevel.name
        )
        
        firestore.collection("users").document(username).set(userData).get()
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
            "schedule" to json.encodeToString(schedulePacker.pack(WeeklyScheduleDto.Companion.empty())),
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

    override fun setForcedInstanceState(instance: InstanceRef, state: DeviceStateDto?) {
        val doc = (instance as FirestoreInstanceRef).document
        doc.reference.update(
            "forcedDeviceState", state?.deviceState,
            "forcedDeviceStateExtra", state?.extra
        ).get()
    }

    override fun updateClientInformation(instance: InstanceRef, clientInfo: ClientInfoDto) {
        val doc = (instance as FirestoreInstanceRef).document

        val knownAppsJson = json.encodeToString(
            clientInfo.knownApps.mapValues {
                FirestoreKnownApp(
                    appName = it.value.appName,
                    iconBase64 = it.value.iconBase64Png)
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
        val doc = (instance as FirestoreInstanceRef).document
        doc.reference.update("associatedAppGroupId", groupId).get()
    }

    override fun getAvailableDeviceStateTypes(instance: InstanceRef): List<DeviceStateTypeDto> {
        val instanceDoc = (instance as FirestoreInstanceRef).document

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

    // App group operations
    override fun createAppGroup(username: String, groupName: String): AppGroupDto {
        val groupId = UUID.randomUUID().toString()
        val now = Clock.System.now()
        
        // Get existing groups to determine next color
        val existingGroups = getAppGroups(username)
        val usedColors = existingGroups.map { it.color }.toSet()
        val nextColor = AppGroupColorPalette.getNextColor(usedColors)
        
        val groupData = mapOf(
            "id" to groupId,
            "name" to groupName,
            "color" to nextColor,
            "createdAt" to now.toString()
        )
        
        firestore.collection("users")
            .document(username)
            .collection("appGroups")
            .document(groupId)
            .set(groupData)
            .get()
            
        return AppGroupDto(
            id = groupId,
            name = groupName,
            color = nextColor,
            createdAt = now
        )
    }

    override fun getAppGroups(username: String): List<AppGroupDto> {
        val groups = firestore.collection("users")
            .document(username)
            .collection("appGroups")
            .orderBy("name")
            .get()
            .get()
            
        return groups.documents.map { doc ->
            AppGroupDto(
                id = doc.getString("id") ?: "",
                name = doc.getString("name") ?: "",
                color = doc.getString("color") ?: AppGroupColorPalette.getDefaultColor(),
                createdAt = doc.getString("createdAt")?.let { Instant.parse(it) } ?: Instant.DISTANT_PAST
            )
        }
    }

    override fun renameAppGroup(username: String, groupId: String, newName: String): AppGroupDto {
        val groupRef = firestore.collection("users")
            .document(username)
            .collection("appGroups")
            .document(groupId)
            
        // Update the group name
        groupRef.update("name", newName).get()
        
        // Get the updated group data
        val updatedGroup = groupRef.get().get()
        return AppGroupDto(
            id = updatedGroup.getString("id") ?: groupId,
            name = updatedGroup.getString("name") ?: newName,
            color = updatedGroup.getString("color") ?: AppGroupColorPalette.getDefaultColor(),
            createdAt = updatedGroup.getString("createdAt")?.let { Instant.parse(it) } ?: Instant.DISTANT_PAST
        )
    }

    override fun deleteAppGroup(username: String, groupId: String) {
        // First, remove all memberships for this group
        val memberships = firestore.collectionGroup("appGroupMemberships")
            .whereEqualTo("groupId", groupId)
            .get()
            .get()
            
        val batch = firestore.batch()
        memberships.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        
        // Delete the group itself
        firestore.collection("users")
            .document(username)
            .collection("appGroups")
            .document(groupId)
            .delete()
            .get()
            
        batch.commit().get()
    }

    override fun addAppToGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String) {
        val membershipId = "${instanceId}_${appPath}_${groupId}".hashCode().toString()
        
        val membershipData = mapOf(
            "appPath" to appPath,
            "groupId" to groupId,
            "instanceId" to instanceId.toString(),
            "username" to username
        )
        
        firestore.collection("users")
            .document(username)
            .collection("instances")
            .document(instanceId.toString())
            .collection("appGroupMemberships")
            .document(membershipId)
            .set(membershipData)
            .get()
    }

    override fun removeAppFromGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String) {
        val membershipId = "${instanceId}_${appPath}_${groupId}".hashCode().toString()
        
        firestore.collection("users")
            .document(username)
            .collection("instances")
            .document(instanceId.toString())
            .collection("appGroupMemberships")
            .document(membershipId)
            .delete()
            .get()
    }

    override fun getAppGroupMemberships(instance: InstanceRef): List<AppGroupMembershipDto> {
        val instanceDoc = (instance as FirestoreInstanceRef).document
        val memberships = instanceDoc.reference
            .collection("appGroupMemberships")
            .get()
            .get()

        return memberships.documents.map { doc ->
            AppGroupMembershipDto(
                appPath = doc.getString("appPath") ?: "",
                groupId = doc.getString("groupId") ?: ""
            )
        }
    }

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
}

@Serializable
class FirestoreKnownApp(
    val appName: String,
    val iconBase64: String?
)

class FirestoreInstanceRef(val document: QueryDocumentSnapshot) : InstanceRef
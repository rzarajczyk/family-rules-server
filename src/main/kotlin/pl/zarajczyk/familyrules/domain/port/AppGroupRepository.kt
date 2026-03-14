package pl.zarajczyk.familyrules.domain.port

import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.GroupStateDetails

interface AppGroupRepository {
    fun createAppGroup(userRef: UserRef, groupId: String, name: String, description: String): AppGroupRef
    fun get(userRef: UserRef, groupId: String): AppGroupRef?
    fun getAll(userRef: UserRef): List<AppGroupRef>
    fun rename(appGroupRef: AppGroupRef, newName: String)
    fun updateDescription(appGroupRef: AppGroupRef, newDescription: String)
    fun delete(appGroupRef: AppGroupRef)

    fun setMembers(appGroupRef: AppGroupRef, deviceRef: DeviceRef, apps: Set<AppTechnicalId>)
    fun getMembers(appGroupRef: AppGroupRef, deviceRef: DeviceRef): Set<AppTechnicalId>

    fun createGroupState(appGroupRef: AppGroupRef, stateId: String, name: String, deviceStates: Map<DeviceId, DeviceStateDto?>): GroupStateRef
    fun getGroupState(appGroupRef: AppGroupRef, stateId: String): GroupStateRef?
    fun getAllGroupStates(appGroupRef: AppGroupRef): List<GroupStateRef>
    fun updateGroupState(appGroupRef: AppGroupRef, stateId: String, name: String, deviceStates: Map<DeviceId, DeviceStateDto?>)
    fun deleteGroupState(appGroupRef: AppGroupRef, stateId: String)
}

/**
 * Represents abstract reference to the database object related to the given app group.
 * Details are populated eagerly at fetch time — no extra DB read needed.
 */
interface AppGroupRef {
    val details: AppGroupDto
}

/**
 * Represents abstract reference to the database object related to the given group state.
 * Group states are embedded inside their parent appGroup document, so a ref carries both
 * the appGroupRef and the stateId needed to locate the state.
 * Details are populated eagerly at fetch time — no extra DB read needed.
 */
interface GroupStateRef {
    val appGroupRef: AppGroupRef
    val stateId: String
    val details: GroupStateDetails
}

typealias AppTechnicalId = String

data class AppGroupDto(
    val id: String,
    val name: String,
    val description: String = "",
    /** members[deviceId] = set of app technical IDs belonging to this group on that device */
    val members: Map<String, Set<AppTechnicalId>> = emptyMap(),
    /** states[stateId] = group state details */
    val states: Map<String, GroupStateDetails> = emptyMap(),
)

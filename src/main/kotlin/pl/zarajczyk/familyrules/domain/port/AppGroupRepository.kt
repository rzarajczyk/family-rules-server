package pl.zarajczyk.familyrules.domain.port

import pl.zarajczyk.familyrules.domain.AppGroupDto
import pl.zarajczyk.familyrules.domain.DeviceRef

interface AppGroupRepository {
    fun createAppGroup(userRef: UserRef, groupId: String, name: String, color: String): AppGroupRef
    fun get(userRef: UserRef, groupId: String): AppGroupRef?
    fun getAll(userRef: UserRef): List<AppGroupRef>
    fun fetchDetails(appGroupRef: AppGroupRef): AppGroupDto
    fun rename(appGroupRef: AppGroupRef, newName: String)
    fun delete(appGroupRef: AppGroupRef)

    fun addMember(appGroupRef: AppGroupRef, deviceRef: DeviceRef, appTechnicalId: String)
    fun removeMember(appGroupRef: AppGroupRef, deviceRef: DeviceRef, appTechnicalId: String)
    fun getMembers(appGroupRef: AppGroupRef, deviceRef: DeviceRef): Set<AppTechnicalId>
}

/**
 * Represents abstract reference to the database object related to the given app group
 */
interface AppGroupRef
typealias AppTechnicalId = String
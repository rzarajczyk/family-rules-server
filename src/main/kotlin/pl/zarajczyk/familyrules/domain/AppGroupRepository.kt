package pl.zarajczyk.familyrules.domain

import pl.zarajczyk.familyrules.api.v2.App

interface AppGroupRepository {
    fun createAppGroup(userRef: UserRef, groupId: String, name: String, color: String): AppGroupRef
    fun get(userRef: UserRef, groupId: String): AppGroupRef?
    fun getAll(userRef: UserRef): List<AppGroupRef>
    fun fetchDetails(appGroupRef: AppGroupRef): AppGroupDto

    fun renameAppGroup(username: String, groupId: String, newName: String): AppGroupDto
    fun deleteAppGroup(username: String, groupId: String)
    fun addAppToGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String)
    fun removeAppFromGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String)
    fun getAppGroupMemberships(instance: InstanceRef): List<AppGroupMembershipDto>
    fun getAppGroupMemberships(username: String): List<AppGroupMembershipDto>
}

/**
 * Represents abstract reference to the database object related to the given app group
 */
interface AppGroupRef
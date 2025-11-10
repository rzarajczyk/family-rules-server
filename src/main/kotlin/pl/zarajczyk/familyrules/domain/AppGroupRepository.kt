package pl.zarajczyk.familyrules.domain

interface AppGroupRepository {
    fun createAppGroup(userRef: UserRef, groupId: String, name: String, color: String): AppGroupRef
    fun get(userRef: UserRef, groupId: String): AppGroupRef?
    fun getAll(userRef: UserRef): List<AppGroupRef>
    fun fetchDetails(appGroupRef: AppGroupRef): AppGroupDto
    fun rename(appGroupRef: AppGroupRef, newName: String)
    fun delete(appGroupRef: AppGroupRef)

    fun addAppToGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String)
    fun removeAppFromGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String)
    fun getAppGroupMemberships(instance: InstanceRef): List<AppGroupMembershipDto>
    fun getAppGroupMemberships(username: String): List<AppGroupMembershipDto>
}

/**
 * Represents abstract reference to the database object related to the given app group
 */
interface AppGroupRef
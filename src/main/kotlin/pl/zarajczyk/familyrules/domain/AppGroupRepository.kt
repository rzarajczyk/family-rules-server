package pl.zarajczyk.familyrules.domain

interface AppGroupRepository {
    fun createAppGroup(username: String, groupName: String): AppGroupDto
    fun getAppGroups(username: String): List<AppGroupDto>
    fun renameAppGroup(username: String, groupId: String, newName: String): AppGroupDto
    fun deleteAppGroup(username: String, groupId: String)
    fun addAppToGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String)
    fun removeAppFromGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String)
    fun getAppGroupMemberships(instance: InstanceRef): List<AppGroupMembershipDto>
    fun getAppGroupMemberships(username: String): List<AppGroupMembershipDto>
}
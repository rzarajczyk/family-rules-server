package pl.zarajczyk.familyrules.shared

import kotlinx.datetime.LocalDate

interface DataRepository {
    
    // User operations
    fun findUser(username: String): UserDto?
    fun validatePassword(username: String, password: String)
    fun changePassword(username: String, newPassword: String)
    fun getAllUsers(): List<UserDto>
    fun deleteUser(username: String)
    fun createUser(username: String, password: String, accessLevel: AccessLevel)

    fun findInstance(id: InstanceId): InstanceRef?
    fun findInstances(username: String): List<InstanceRef>
    
    // Instance operations
    fun validateInstanceToken(instanceId: InstanceId, instanceToken: String): InstanceId?
    fun setupNewInstance(username: String, instanceName: String, clientType: String): NewInstanceDto
    fun getInstance(instance: InstanceRef): InstanceDto
    fun updateInstance(instance: InstanceRef, update: UpdateInstanceDto)
    fun deleteInstance(instance: InstanceRef)
    fun setInstanceSchedule(instance: InstanceRef, schedule: WeeklyScheduleDto)
    fun setForcedInstanceState(instance: InstanceRef, state: DeviceState?)
    fun updateClientInformation(instance: InstanceRef, clientInfo: ClientInfoDto)
    
    // Device states operations
    fun getAvailableDeviceStates(instance: InstanceRef): List<DescriptiveDeviceStateDto>
    
    // Screen time operations
    fun saveReport(instance: InstanceRef, day: LocalDate, screenTimeSeconds: Long, applicationsSeconds: Map<String, Long>)
    fun getScreenTimes(instance: InstanceRef, day: LocalDate): ScreenTimeDto
    
    // App group operations
    fun createAppGroup(username: String, groupName: String): AppGroupDto
    fun getAppGroups(username: String): List<AppGroupDto>
    fun renameAppGroup(username: String, groupId: String, newName: String): AppGroupDto
    fun deleteAppGroup(username: String, groupId: String)
    fun addAppToGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String)
    fun removeAppFromGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String)
    fun getAppGroupMemberships(username: String, instanceId: InstanceId): List<AppGroupMembershipDto>
}
package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.LocalDate

interface DataRepository {
    fun findInstance(id: InstanceId): InstanceRef?
    fun findInstances(username: String): List<InstanceRef>
    
    // Instance operations
    fun validateInstanceToken(instanceId: InstanceId, instanceToken: String): InstanceId?
    fun setupNewInstance(username: String, instanceName: String, clientType: String): NewInstanceDto
    fun getInstance(instance: InstanceRef): InstanceDto
    fun getInstanceBasicData(instance: InstanceRef): BasicInstanceDto
    fun updateInstance(instance: InstanceRef, update: UpdateInstanceDto)
    fun deleteInstance(instance: InstanceRef)
    fun setInstanceSchedule(instance: InstanceRef, schedule: WeeklyScheduleDto)
    fun setForcedInstanceState(instance: InstanceRef, state: DeviceStateDto?)
    fun updateClientInformation(instance: InstanceRef, clientInfo: ClientInfoDto)
    fun setAssociatedAppGroup(instance: InstanceRef, groupId: String?)
    
    // Device states operations
    fun getAvailableDeviceStateTypes(instance: InstanceRef): List<DeviceStateTypeDto>
    
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
    fun getAppGroupMemberships(instance: InstanceRef): List<AppGroupMembershipDto>
    fun getAppGroupMemberships(username: String): List<AppGroupMembershipDto>
}
package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.LocalDate

interface DevicesRepository {
    fun get(id: DeviceId): DeviceRef?
    fun getAll(username: String): List<DeviceRef>
    
    // Instance operations
    fun validateInstanceToken(instanceId: DeviceId, instanceToken: String): DeviceId?
    fun setupNewInstance(username: String, instanceName: String, clientType: String): NewInstanceDto
    fun fetchDetails(instance: DeviceRef): InstanceDto
    fun getInstanceBasicData(instance: DeviceRef): BasicInstanceDto
    fun updateInstance(instance: DeviceRef, update: UpdateInstanceDto)
    fun delete(instance: DeviceRef)
    fun setInstanceSchedule(instance: DeviceRef, schedule: WeeklyScheduleDto)
    fun setForcedInstanceState(instance: DeviceRef, state: DeviceStateDto?)
    fun updateClientInformation(instance: DeviceRef, clientInfo: ClientInfoDto)
    fun setAssociatedAppGroup(instance: DeviceRef, groupId: String?)
    
    // Device states operations
    fun getAvailableDeviceStateTypes(instance: DeviceRef): List<DeviceStateTypeDto>
    
    // Screen time operations
    fun saveReport(instance: DeviceRef, day: LocalDate, screenTimeSeconds: Long, applicationsSeconds: Map<String, Long>)
    fun getScreenTimes(instance: DeviceRef, day: LocalDate): ScreenTimeDto

    fun getOwner(deviceRef: DeviceRef): UserRef
}


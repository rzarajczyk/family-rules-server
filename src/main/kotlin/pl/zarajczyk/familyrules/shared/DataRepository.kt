package pl.zarajczyk.familyrules.shared

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.util.*

interface DataRepository {
    
    // User operations
    fun findUser(username: String): UserDto?
    fun validatePassword(username: String, password: String)
    fun changePassword(username: String, newPassword: String)

    fun getInstanceReference(id: InstanceId): DbInstanceReference?
    
    // Instance operations
    fun validateInstanceToken(instanceId: InstanceId, instanceToken: String): InstanceId?
    fun setupNewInstance(username: String, instanceName: String, clientType: String): NewInstanceDto
    fun getInstances(username: String): List<InstanceDto>
    fun getInstance(instanceId: InstanceId): InstanceDto?
    fun getInstance(instance: DbInstanceReference): InstanceDto
    fun updateInstance(instanceId: InstanceId, update: UpdateInstanceDto)
    fun deleteInstance(instanceId: InstanceId)
    fun setInstanceSchedule(id: InstanceId, schedule: WeeklyScheduleDto)
    fun setForcedInstanceState(id: InstanceId, state: DeviceState?)
    fun updateClientInformation(id: InstanceId, version: String, timezoneOffsetSeconds: Int)
    
    // Device states operations
    fun getAvailableDeviceStates(id: InstanceId): List<DescriptiveDeviceStateDto>
    fun updateAvailableDeviceStates(id: InstanceId, states: List<DescriptiveDeviceStateDto>)
    
    // Screen time operations
    fun saveReport(instance: DbInstanceReference, day: LocalDate, screenTimeSeconds: Long, applicationsSeconds: Map<String, Long>)
    fun getScreenTimes(id: InstanceId, day: LocalDate): ScreenTimeDto
}

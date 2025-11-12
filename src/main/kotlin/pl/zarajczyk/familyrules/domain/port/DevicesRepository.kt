package pl.zarajczyk.familyrules.domain.port

import kotlinx.datetime.LocalDate
import pl.zarajczyk.familyrules.domain.BasicDeviceDto
import pl.zarajczyk.familyrules.domain.ClientInfoDto
import pl.zarajczyk.familyrules.domain.DeviceDto
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.DeviceRef
import pl.zarajczyk.familyrules.domain.DeviceStateDto
import pl.zarajczyk.familyrules.domain.DeviceStateTypeDto
import pl.zarajczyk.familyrules.domain.NewDeviceDto
import pl.zarajczyk.familyrules.domain.ScreenTimeDto
import pl.zarajczyk.familyrules.domain.UpdateInstanceDto
import pl.zarajczyk.familyrules.domain.WeeklyScheduleDto

interface DevicesRepository {
    fun get(id: DeviceId): DeviceRef?
    fun getAll(username: String): List<DeviceRef>
    fun getByName(user: UserRef, deviceName: String): DeviceRef?

    fun validateDeviceToken(deviceId: DeviceId, deviceToken: String): DeviceId?
    fun createNewDevice(user: UserRef, instanceName: String, clientType: String): NewDeviceDto
    fun fetchDetails(instance: DeviceRef): DeviceDto
    fun fetchBasicData(instance: DeviceRef): BasicDeviceDto
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
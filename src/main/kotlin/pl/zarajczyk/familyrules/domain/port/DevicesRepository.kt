package pl.zarajczyk.familyrules.domain.port

import kotlinx.datetime.LocalDate
import pl.zarajczyk.familyrules.domain.*

interface DevicesRepository {
    fun createDevice(user: UserRef, details: DeviceDetailsDto): DeviceRef
    fun get(id: DeviceId): DeviceRef?
    fun getAll(username: String): List<DeviceRef>
    fun getByName(user: UserRef, deviceName: String): DeviceRef?
    fun fetchDetails(device: DeviceRef, includePasswordHash: Boolean = false): DeviceDetailsDto
    fun fetchDeviceDto(device: DeviceRef): DeviceDto
    fun delete(device: DeviceRef)

    fun updateInstance(device: DeviceRef, update: UpdateInstanceDto)

    fun setInstanceSchedule(device: DeviceRef, schedule: WeeklyScheduleDto)
    fun setForcedInstanceState(device: DeviceRef, state: DeviceStateDto?)
    fun updateClientInformation(device: DeviceRef, clientInfo: ClientInfoDto)
    fun setAssociatedAppGroup(device: DeviceRef, groupId: String?)

    // Device states operations
    fun getAvailableDeviceStateTypes(instance: DeviceRef): List<DeviceStateTypeDto>

    // Screen time operations
    fun saveReport(instance: DeviceRef, day: LocalDate, screenTimeSeconds: Long, applicationsSeconds: Map<String, Long>)
    fun getScreenTimes(instance: DeviceRef, day: LocalDate): ScreenTimeDto

    fun getOwner(deviceRef: DeviceRef): UserRef
}

interface InstanceRef
typealias DeviceRef = InstanceRef

data class DeviceDetailsDto(
    val deviceId: DeviceId,
    val deviceName: String,
    val forcedDeviceState: DeviceStateDto?,
    val hashedToken: String,
    val clientType: String,
    val clientVersion: String,
    // schedule
    val clientTimezoneOffsetSeconds: Long,
    val deleted: Boolean,
    val iconData: String?,
    val iconType: String?,
    val reportIntervalSeconds: Long?,
    )
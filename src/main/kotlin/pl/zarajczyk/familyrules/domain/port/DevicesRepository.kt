package pl.zarajczyk.familyrules.domain.port

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import pl.zarajczyk.familyrules.domain.AppDto
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.DeviceStateTypeDto
import pl.zarajczyk.familyrules.domain.ScreenReportDto
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.leaveUnchanged
import java.util.concurrent.atomic.AtomicReference

interface DevicesRepository {
    fun createDevice(user: UserRef, details: DeviceDetailsDto, tokenHash: String): DeviceRef
    fun get(id: DeviceId): DeviceRef?
    fun getAll(userRef: UserRef): List<DeviceRef>
    fun getByName(user: UserRef, deviceName: String): DeviceRef?
    fun delete(device: DeviceRef)
    fun update(device: DeviceRef, details: DeviceDetailsUpdateDto)
    fun getOwner(deviceRef: DeviceRef): UserRef

    fun updateOwnerLastActivity(deviceRef: DeviceRef, lastActivityMillis: Long)

    // Screen time operations
    fun setScreenReport(device: DeviceRef, day: LocalDate, screenReportDto: ScreenReportDto)
    fun getScreenReport(device: DeviceRef, day: LocalDate): ScreenReportDto?
}

interface DeviceRef {
    val details: DeviceDetailsDto
    val tokenHash: String
    fun getDeviceId(): DeviceId
}

data class DeviceDetailsDto(
    val deviceId: DeviceId,
    val deviceName: String,
    val forcedDeviceState: DeviceStateDto?,
    val clientType: String,
    val clientVersion: String,
    val clientTimezoneOffsetSeconds: Long,
    val iconData: String?,
    val iconType: String?,
    val reportIntervalSeconds: Long,
    val knownApps: Map<String, AppDto>,
    val availableDeviceStates: List<DeviceStateTypeDto>,
    val appGroups: AppGroupsDto = AppGroupsDto.empty()
)

data class DeviceDetailsUpdateDto(
    val deviceId: ValueUpdate<DeviceId> = leaveUnchanged(),
    val deviceName: ValueUpdate<String> = leaveUnchanged(),
    val forcedDeviceState: ValueUpdate<DeviceStateDto?> = leaveUnchanged(),
    val hashedToken: ValueUpdate<String> = leaveUnchanged(),
    val clientType: ValueUpdate<String> = leaveUnchanged(),
    val clientVersion: ValueUpdate<String> = leaveUnchanged(),
    val clientTimezoneOffsetSeconds: ValueUpdate<Long> = leaveUnchanged(),
    val iconData: ValueUpdate<String?> = leaveUnchanged(),
    val iconType: ValueUpdate<String?> = leaveUnchanged(),
    val reportIntervalSeconds: ValueUpdate<Long> = leaveUnchanged(),
    val knownApps: ValueUpdate<Map<String, AppDto>> = leaveUnchanged(),
    val availableDeviceStates: ValueUpdate<List<DeviceStateTypeDto>> = leaveUnchanged(),
    val appGroups: ValueUpdate<AppGroupsDto> = leaveUnchanged()
)

data class ValueUpdate<T>(
    val value: AtomicReference<T>?
) {
    companion object {
        fun <T> set(value: T) = ValueUpdate(AtomicReference(value))
        fun <T> leaveUnchanged() = ValueUpdate<T>(null)
    }

    fun <K> ifPresent(fn: (T) -> K): K? {
        return value?.let { fn(value.get()) }
    }
}

const val DEFAULT_DEVICE_STATE = "ACTIVE"

@Serializable
data class DeviceStateDto(
    val deviceState: String,
    val extra: String? = null
) {
    companion object {
        fun default() = DeviceStateDto(DEFAULT_DEVICE_STATE, null)
    }
}

@Serializable
data class AppGroupsDto(
    val show: List<String> = emptyList(),
    val block: List<String> = emptyList()
) {
    companion object {
        fun empty() = AppGroupsDto(emptyList(), emptyList())
    }
}
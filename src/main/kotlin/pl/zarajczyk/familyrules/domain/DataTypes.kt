package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable


data class NewDeviceDto(
    val instanceId: InstanceId,
    val token: String
)

data class ScreenTimeDto(
    val screenTimeSeconds: Long,
    val applicationsSeconds: Map<String, Long>,
    val updatedAt: Instant
)

data class DeviceDto(
    val id: InstanceId,
    val name: String,
    val forcedDeviceState: DeviceStateDto?,
    val clientType: String,
    val clientVersion: String,
    val schedule: WeeklyScheduleDto,
    val clientTimezoneOffsetSeconds: Int,
    val iconData: String? = null,
    val iconType: String? = null,
    val reportIntervalSeconds: Int?,
    val knownApps: Map<String, AppDto>,
    val associatedAppGroupId: String? = null
)

data class BasicDeviceDto(
    val id: InstanceId,
    val name: String,
)

data class UpdateInstanceDto(
    val instanceId: InstanceId,
    val name: String,
    val iconData: String?,
    val iconType: String?
)

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
data class DeviceStateTypeDto(
    val deviceState: String,
    val title: String,
    val icon: String?,
    val description: String?,
    val arguments: Set<DeviceStateArgument>
)

@Serializable
enum class DeviceStateArgument { APP_GROUP }

data class AppDto(
    val appName: String,
    val iconBase64Png: String?
)

data class ClientInfoDto(
    val version: String,
    val timezoneOffsetSeconds: Int,
    val reportIntervalSeconds: Int?,
    val knownApps: Map<String, AppDto>,
    val states: List<DeviceStateTypeDto>
)

@Serializable
data class WeeklyScheduleDto(
    val schedule: Map<DayOfWeek, DailyScheduleDto>
) {
    companion object {
        fun empty() = WeeklyScheduleDto(DayOfWeek.entries.associateWith { DailyScheduleDto(emptyList()) })
    }
}

@Serializable
data class DailyScheduleDto(
    val periods: List<PeriodDto>
)

@Serializable
data class PeriodDto(
    val fromSeconds: Long,
    val toSeconds: Long,
    val deviceState: DeviceStateDto
)


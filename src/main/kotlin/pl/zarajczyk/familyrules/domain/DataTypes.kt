package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.*

typealias InstanceId = UUID

class InvalidPassword : RuntimeException("Invalid password")
class IllegalInstanceName(val instanceName: String) : RuntimeException("Instance $instanceName already exists")
class InstanceAlreadyExists(val instanceName: String) : RuntimeException("Instance $instanceName has incorrect name")

enum class AccessLevel {
    ADMIN,
    PARENT
}

data class UserDto(
    val username: String,
    val passwordSha256: String,
    val accessLevel: AccessLevel = AccessLevel.ADMIN
)

data class NewInstanceDto(
    val instanceId: InstanceId,
    val token: String
)

data class ScreenTimeDto(
    val screenTimeSeconds: Long,
    val applicationsSeconds: Map<String, Long>,
    val updatedAt: Instant
)

data class InstanceDto(
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

data class AppGroupDto(
    val id: String,
    val name: String,
    val color: String,
    val createdAt: Instant
)

data class AppGroupMembershipDto(
    val appPath: String,
    val groupId: String
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

interface InstanceRef
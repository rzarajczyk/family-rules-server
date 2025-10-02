package pl.zarajczyk.familyrules.shared

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable
import java.util.*

typealias InstanceId = UUID

class InvalidPassword : RuntimeException("Invalid password")
class IllegalInstanceName(val instanceName: String) : RuntimeException("Instance $instanceName already exists")
class InstanceAlreadyExists(val instanceName: String) : RuntimeException("Instance $instanceName has incorrect name")

data class UserDto(
    val username: String,
    val passwordSha256: String
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
    val forcedDeviceState: DeviceState?,
    val clientType: String,
    val clientVersion: String,
    val schedule: WeeklyScheduleDto,
    val clientTimezoneOffsetSeconds: Int,
    val iconData: String? = null,
    val iconType: String? = null,
)

data class UpdateInstanceDto(
    val instanceId: InstanceId,
    val name: String,
    val iconData: String?,
    val iconType: String?
)

typealias DeviceState = String

const val DEFAULT_STATE: DeviceState = "ACTIVE"

data class DescriptiveDeviceStateDto(
    val deviceState: DeviceState,
    val title: String,
    val icon: String?,
    val description: String?
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
    val deviceState: DeviceState
)

interface DbInstanceReference
package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto

data class ScreenReportDto(
    val screenTimeSeconds: Long,
    val applicationsSeconds: Map<String, Long>,
    val updatedAt: Instant,
    val screenTimeHistogram: Map<String, Long>,
    val lastUpdatedApps: Set<String>
) {
    companion object {
        fun empty() = ScreenReportDto(
            screenTimeSeconds = 0,
            applicationsSeconds = emptyMap(),
            updatedAt = Instant.DISTANT_PAST,
            screenTimeHistogram = emptyMap(),
            lastUpdatedApps = emptySet()
        )
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


package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

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



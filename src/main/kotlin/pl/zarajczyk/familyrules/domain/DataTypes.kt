package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

data class ScreenReportDto(
    val screenTimeSeconds: Long,
    val applicationsSeconds: Map<String, Long>,
    val appUsageBuckets: Map<String, Set<String>>,
    val updatedAt: Instant,
    val onlinePeriods: Set<String>,
    val lastUpdatedApps: Set<String>
) {
    companion object {
        fun empty() = ScreenReportDto(
            screenTimeSeconds = 0,
            applicationsSeconds = emptyMap(),
            appUsageBuckets = emptyMap(),
            updatedAt = Instant.DISTANT_PAST,
            onlinePeriods = emptySet(),
            lastUpdatedApps = emptySet()
        )
    }
}

data class SetScreenReportDto(
    val screenTimeSeconds: Long,
    val applicationsSeconds: Map<String, Long>,
    val updatedAt: Instant,
    val currentOnlinePeriodBucket: String,
    val currentOnlinePeriods: Set<String>,
    val lastUpdatedApps: Set<String>,
    val currentAppBucketDeltas: Map<String, Long>
)

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
    val iconWebp: ByteArray?
)

data class ClientInfoDto(
    val version: String,
    val timezoneOffsetSeconds: Int,
    val reportIntervalSeconds: Int?,
    val knownApps: Map<String, AppDto>,
    val states: List<DeviceStateTypeDto>
)

package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class AppGroupService(private val dataRepository: DataRepository, private val appGroupRepository: AppGroupRepository) {
    fun getReport(
        username: String,
        day: LocalDate
    ): List<AppGroupReport> {
        val instances = dataRepository.findInstances(username)
        val appGroups = appGroupRepository.getAppGroups(username)

        val groupStats = appGroups.map { group ->
            // Calculate statistics across all instances
            var totalApps = 0
            var totalScreenTimeSeconds = 0L
            val deviceCount = mutableSetOf<String>()
            val appDetails = mutableListOf<AppGroupAppReport>()

            instances.forEach { instanceRef ->
                val instance = dataRepository.getInstance(instanceRef)
                val screenTimeDto = dataRepository.getScreenTimes(instanceRef, day)
                val instanceMemberships = appGroupRepository.getAppGroupMemberships(instanceRef)
                    .filter { it.groupId == group.id }

                if (instanceMemberships.isNotEmpty()) {
                    deviceCount.add(instance.id.toString())
                    totalApps += instanceMemberships.size

                    // Collect detailed app information for this group
                    instanceMemberships.forEach { membership ->
                        val appScreenTimeSeconds = screenTimeDto.applicationsSeconds[membership.appPath] ?: 0L
                        totalScreenTimeSeconds += appScreenTimeSeconds

                        // Get app name and icon from known apps or use the path
                        val knownApp = instance.knownApps[membership.appPath]
                        val appName = knownApp?.appName ?: membership.appPath
                        val appIcon = knownApp?.iconBase64Png

                        appDetails.add(
                            AppGroupAppReport(
                                name = appName,
                                packageName = membership.appPath,
                                deviceName = instance.name,
                                deviceId = instance.id.toString(),
                                screenTime = appScreenTimeSeconds,
                                percentage = 0.0, // Will be calculated below
                                iconBase64 = appIcon
                            )
                        )
                    }
                }
            }

            // Calculate percentages for each app
            val appsWithPercentages = if (totalScreenTimeSeconds > 0) {
                appDetails.map { app ->
                    app.copy(percentage = (app.screenTime.toDouble() / totalScreenTimeSeconds * 100).let {
                        (it * 100).toInt().toDouble() / 100 // Round to 2 decimal places
                    })
                }
            } else {
                appDetails
            }.sortedByDescending { it.screenTime }

            val colorInfo = AppGroupColorPalette.getColorInfo(group.color)
            AppGroupReport(
                id = group.id,
                name = group.name,
                color = group.color,
                textColor = colorInfo?.text ?: "#000000",
                appsCount = totalApps,
                devicesCount = deviceCount.size,
                totalScreenTime = totalScreenTimeSeconds,
                apps = appsWithPercentages
            )
        }

        return groupStats
    }
}

data class AppGroupReport(
    val id: String,
    val name: String,
    val color: String,
    val textColor: String,
    val appsCount: Int,
    val devicesCount: Int,
    val totalScreenTime: Long,
    val apps: List<AppGroupAppReport> = emptyList()
)

data class AppGroupAppReport(
    val name: String,
    val packageName: String,
    val deviceName: String,
    val deviceId: String,
    val screenTime: Long,
    val percentage: Double,
    val iconBase64: String? = null
)
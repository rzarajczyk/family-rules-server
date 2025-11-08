package pl.zarajczyk.familyrules.api.v2

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.*

@RestController
class V2AppGroupController(private val dataRepository: DataRepository) {
    @PostMapping(value = ["/api/v2/group-membership-for-device", "/api/v2/group-report"])
    fun getMembership(@RequestBody request: AppGroupMembershipRequest, authentication: Authentication): AppGroupMembershipResponse {
        val instanceRef = dataRepository.findAuthenticatedInstance(authentication)
        val instance = dataRepository.getInstance(instanceRef)

        val memberships = dataRepository.getAppGroupMemberships(instanceRef)
        val groupMemberships = memberships.filter { it.groupId == request.appGroupId }

        val apps = groupMemberships.associate { membership ->
            val known = instance.knownApps[membership.appPath]
            membership.appPath to App(
                appName = known?.appName ?: membership.appPath,
                iconBase64Png = known?.iconBase64Png
            )
        }

        return AppGroupMembershipResponse(
            appGroupId = request.appGroupId,
            apps = apps.mapValues { (_, app) -> 
                AppGroupApp(
                    appName = app.appName,
                    iconBase64Png = app.iconBase64Png,
                    deviceName = instance.name,
                    deviceId = instance.id.toString()
                )
            }
        )
    }

    @PostMapping("/api/v2/groups-usage-report")
    fun getAppGroupsUsageReport(authentication: Authentication): AppGroupsUsageReportResponse {
        val instanceRef = dataRepository.findAuthenticatedInstance(authentication)

        // Get username from the instance document path
        // The path is /users/{username}/instances/{instanceId}
        val username = (instanceRef as pl.zarajczyk.familyrules.adapter.firestore.FirestoreInstanceRef)
            .document.reference.parent.parent?.id
            ?: throw RuntimeException("Cannot determine username from instance")

        val today = today()
        val appGroups = dataRepository.getAppGroups(username)
        val instances = dataRepository.findInstances(username)

        // Build usage report for each app group
        val appGroupsUsage = appGroups.map { group ->
            var totalTimeSeconds = 0L
            val appsUsage: MutableMap<String, AppUsageReport> = mutableMapOf()

            instances.forEach { instanceRef ->
                val instance = dataRepository.getInstance(instanceRef)
                val screenTimeDto = dataRepository.getScreenTimes(instanceRef, today)
                val instanceMemberships = dataRepository.getAppGroupMemberships(instanceRef)
                    .filter { it.groupId == group.id }
                if (instanceMemberships.isNotEmpty()) {
                    instanceMemberships.forEach { membership ->
                        val appScreenTimeSeconds = screenTimeDto.applicationsSeconds[membership.appPath] ?: 0L
                        totalTimeSeconds += appScreenTimeSeconds

                        val knownApp = instance.knownApps[membership.appPath]

                        val appPath = membership.appPath
                        val appName = knownApp?.appName ?: membership.appPath
                        val appIcon = knownApp?.iconBase64Png

                        appsUsage[appPath] = AppUsageReport(
                            app = AppGroupApp(
                                appName = appName,
                                iconBase64Png = appIcon,
                                deviceName = instance.name,
                                deviceId = instance.id.toString()
                            ),
                            uptimeSeconds = appScreenTimeSeconds
                        )
                    }
                }
            }
            
            AppGroupUsageReport(
                appGroupId = group.id,
                appGroupName = group.name,
                apps = appsUsage,
                totalTimeSeconds = totalTimeSeconds
            )
        }

        return AppGroupsUsageReportResponse(
            appGroups = appGroupsUsage
        )
    }
}

data class AppGroupMembershipRequest(
    val appGroupId: String
)

data class AppGroupMembershipResponse(
    val appGroupId: String,
    val apps: Map<String, AppGroupApp>
)

data class AppGroupsUsageReportResponse(
    val appGroups: List<AppGroupUsageReport>
)

data class AppGroupUsageReport(
    val appGroupId: String,
    val appGroupName: String,
    val apps: Map<String, AppUsageReport>,
    val totalTimeSeconds: Long
)

data class AppUsageReport(
    val app: AppGroupApp,
    val uptimeSeconds: Long
)

data class AppGroupApp(
    val appName: String,
    val iconBase64Png: String?,
    val deviceName: String,
    val deviceId: String
)
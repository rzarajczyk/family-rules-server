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
        val instance = dataRepository.getInstance(instanceRef)
        
        // Get username from the instance document path
        // The path is /users/{username}/instances/{instanceId}
        val username = (instanceRef as pl.zarajczyk.familyrules.adapter.firestore.FirestoreInstanceRef)
            .document.reference.parent.parent?.id
            ?: throw RuntimeException("Cannot determine username from instance")
        
        // Get all app groups for the user
        val appGroups = dataRepository.getAppGroups(username)
        
        // Get memberships for this instance
        val memberships = dataRepository.getAppGroupMemberships(instanceRef)
        
        // Get today's screen time data
        val today = today()
        val screenTimeDto = dataRepository.getScreenTimes(instanceRef, today)
        
        // Build usage report for each app group
        val appGroupsUsage = appGroups.map { group ->
            // Filter memberships for this group
            val groupMemberships = memberships.filter { it.groupId == group.id }
            
            // Build app usage map for this group
            val appsUsage = groupMemberships.associate { membership ->
                val appScreenTime = screenTimeDto.applicationsSeconds[membership.appPath] ?: 0L
                val known = instance.knownApps[membership.appPath]
                
                membership.appPath to AppUsageReport(
                    app = AppGroupApp(
                        appName = known?.appName ?: membership.appPath,
                        iconBase64Png = known?.iconBase64Png,
                        deviceName = instance.name,
                        deviceId = instance.id.toString()
                    ),
                    uptimeSeconds = appScreenTime
                )
            }
            
            // Calculate total time for the group
            val totalTimeSeconds = appsUsage.values.sumOf { it.uptimeSeconds }
            
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
package pl.zarajczyk.familyrules.api.v2

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.*

@RestController
class V2AppGroupController(
    private val dataRepository: DataRepository,
    private val appGroupService: AppGroupService
) {
    @PostMapping(value = ["/api/v2/group-membership-for-device", "/api/v2/group-report"])
    fun getMembership(@RequestBody request: MembershipRequest, authentication: Authentication): MembershipResponse {
        val instanceRef = dataRepository.findAuthenticatedInstance(authentication)
        val instance = dataRepository.getInstance(instanceRef)

        val memberships = dataRepository.getAppGroupMemberships(instanceRef)
        val groupMemberships = memberships.filter { it.groupId == request.appGroupId }


        return MembershipResponse(
            appGroupId = request.appGroupId,
            apps = groupMemberships.map {
                val known = instance.knownApps[it.appPath]
                MembershipAppResponse(
                    appPath = it.appPath,
                    appName = known?.appName ?: it.appPath,
                    iconBase64Png = known?.iconBase64Png,
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

        val report = appGroupService.getReport(username, today())

        return AppGroupsUsageReportResponse(
            appGroups = report.map { it.toUsageReport() }
        )
    }

    private fun AppGroupReport.toUsageReport() = AppGroupUsageReportResponse(
        appGroupId = this.id,
        appGroupName = this.name,
        totalTimeSeconds = this.totalScreenTime,
        apps = apps.map {
            AppUsageReportResponse(
                appPath = it.packageName,
                appName = it.name,
                iconBase64Png = it.iconBase64,
                deviceName = it.deviceName,
                deviceId = it.deviceId,
                uptimeSeconds = it.screenTime
            )
        }
    )
}

data class MembershipRequest(
    val appGroupId: String
)

data class MembershipResponse(
    val appGroupId: String,
    val apps: List<MembershipAppResponse>
)

data class MembershipAppResponse(
    val appPath: String,
    val appName: String,
    val iconBase64Png: String?,
    val deviceName: String,
    val deviceId: String,
)

data class AppGroupsUsageReportResponse(
    val appGroups: List<AppGroupUsageReportResponse>
)

data class AppGroupUsageReportResponse(
    val appGroupId: String,
    val appGroupName: String,
    val apps: List<AppUsageReportResponse>,
    val totalTimeSeconds: Long
)

data class AppUsageReportResponse(
    val appPath: String,
    val appName: String,
    val iconBase64Png: String?,
    val deviceName: String,
    val deviceId: String,
    val uptimeSeconds: Long
)
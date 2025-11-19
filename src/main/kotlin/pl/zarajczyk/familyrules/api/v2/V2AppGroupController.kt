package pl.zarajczyk.familyrules.api.v2

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.*

@RestController
class V2AppGroupController(
    private val devicesService: DevicesService,
    private val appGroupService: AppGroupService
) {
    @PostMapping("/api/v2/group-membership-for-device")
    fun getMembership(@RequestBody request: MembershipRequest, authentication: Authentication): MembershipResponse {
        val device = devicesService.get(authentication)
        val deviceDetails = device.fetchDetails()
        val appGroup = appGroupService.get(device.getOwner(), request.appGroupId)
        val appTechnicalIds = appGroup.getMembers(device)
        return MembershipResponse(
            appGroupId = request.appGroupId,
            apps = appTechnicalIds.map { appTechnicalId ->
                val known = deviceDetails.knownApps[appTechnicalId]
                MembershipAppResponse(
                    appPath = appTechnicalId,
                    appName = known?.appName ?: appTechnicalId,
                    iconBase64Png = known?.iconBase64Png,
                    deviceName = deviceDetails.deviceName,
                    deviceId = deviceDetails.deviceId.toString()
                )
            }
        )


    }

    @PostMapping("/api/v2/groups-usage-report")
    fun getAppGroupsUsageReport(authentication: Authentication): AppGroupsUsageReportResponse {
        val device = devicesService.get(authentication)
        val deviceDetails = device.fetchDetails()
        val report = appGroupService.getReport(device.getOwner(), today())
        
        // Filter app groups based on device's appGroups.show configuration
        val selectedAppGroupIds = deviceDetails.appGroups.show
        val filteredReport = if (selectedAppGroupIds.isNotEmpty()) {
            report.filter { it.id in selectedAppGroupIds }
        } else {
            report
        }

        return AppGroupsUsageReportResponse(
            appGroups = filteredReport.map { it.toUsageReport() }
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
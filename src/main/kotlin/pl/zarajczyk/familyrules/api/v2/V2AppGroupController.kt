package pl.zarajczyk.familyrules.api.v2

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.adapter.firestore.FirestoreDeviceRef
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DevicesRepository

@RestController
class V2AppGroupController(
    private val devicesRepository: DevicesRepository,
    private val appGroupService: AppGroupService,
    private val usersService: UsersService
) {
    @PostMapping("/api/v2/group-membership-for-device")
    fun getMembership(@RequestBody request: MembershipRequest, authentication: Authentication): MembershipResponse {
        val instanceRef = devicesRepository.findAuthenticatedDevice(authentication)
        val instance = devicesRepository.fetchDetails(instanceRef)

        return usersService.withUserContext(devicesRepository.getOwner(instanceRef)) { user ->
            appGroupService.withAppGroupContext(user, request.appGroupId) { appGroup ->
                val appTechnicalIds = appGroup.getMembers(instanceRef)
                MembershipResponse(
                    appGroupId = request.appGroupId,
                    apps = appTechnicalIds.map { appTechnicalId ->
                        val known = instance.knownApps[appTechnicalId]
                        MembershipAppResponse(
                            appPath = appTechnicalId,
                            appName = known?.appName ?: appTechnicalId,
                            iconBase64Png = known?.iconBase64Png,
                            deviceName = instance.name,
                            deviceId = instance.id.toString()
                        )
                    }
                )
            }
        }
    }

    @PostMapping("/api/v2/groups-usage-report")
    fun getAppGroupsUsageReport(authentication: Authentication): AppGroupsUsageReportResponse {
        val instanceRef = devicesRepository.findAuthenticatedDevice(authentication)

        // Get username from the instance document path
        // The path is /users/{username}/instances/{instanceId}
        val username = (instanceRef as FirestoreDeviceRef)
            .document.reference.parent.parent?.id
            ?: throw RuntimeException("Cannot determine username from instance")

        val report = usersService.withUserContext(username) { user ->
            appGroupService.getReport(user, today())
        }

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
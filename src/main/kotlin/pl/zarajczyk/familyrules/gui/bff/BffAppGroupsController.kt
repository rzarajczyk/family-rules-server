package pl.zarajczyk.familyrules.gui.bff

import kotlinx.datetime.LocalDate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.AppGroupRepository
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto
import java.util.*

@RestController
class BffAppGroupsController(
    private val usersService: UsersService,
    private val appGroupService: AppGroupService,
    private val devicesService: DevicesService,
    private val groupStateService: GroupStateService,
    private val stateService: StateService,
    private val appGroupRepository: AppGroupRepository
) {

    @PostMapping("/bff/app-groups")
    fun createAppGroup(
        @RequestBody request: CreateAppGroupRequest,
        authentication: Authentication
    ): CreateAppGroupResponse {
        val user = usersService.get(authentication.name)
        val group = appGroupService.createAppGroup(user, request.name)
        return CreateAppGroupResponse(group.getDetails())
    }


    @GetMapping("/bff/app-groups")
    fun getAppGroups(authentication: Authentication): GetAppGroupsResponse {
        val user = usersService.get(authentication.name)
        val groups = appGroupService.listAllAppGroups(user).map { it.getDetails() }
        return GetAppGroupsResponse(groups)
    }


    @DeleteMapping("/bff/app-groups/{groupId}")
    fun deleteAppGroup(
        @PathVariable groupId: String,
        authentication: Authentication
    ): DeleteAppGroupResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        appGroup.delete()
        return DeleteAppGroupResponse(true)
    }


    @PutMapping("/bff/app-groups/{groupId}")
    fun updateAppGroup(
        @PathVariable groupId: String,
        @RequestBody request: UpdateAppGroupRequest,
        authentication: Authentication
    ): UpdateAppGroupResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        appGroup.rename(request.newName)
        appGroup.updateDescription(request.description)
        return UpdateAppGroupResponse(true)
    }

    @PostMapping("/bff/app-groups/{groupId}/apps")
    fun addAppToGroup(
        @PathVariable groupId: String,
        @RequestBody request: AddAppToGroupRequest,
        authentication: Authentication
    ): AddAppToGroupResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        val device = devicesService.get(request.instanceId)
        appGroup.addMember(device, request.appPath)
        return AddAppToGroupResponse(true)
    }

    @DeleteMapping("/bff/app-groups/{groupId}/apps/{appPath}")
    fun removeAppFromGroup(
        @PathVariable groupId: String,
        @PathVariable appPath: String,
        @RequestParam instanceId: UUID,
        authentication: Authentication
    ): RemoveAppFromGroupResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        val device = devicesService.get(instanceId)
        appGroup.removeMember(device, appPath)
        return RemoveAppFromGroupResponse(true)
    }


    @GetMapping("/bff/app-groups/statistics")
    fun getAppGroupStatistics(
        @RequestParam("date") date: String,
        authentication: Authentication
    ): AppGroupStatisticsResponse {
        val day = LocalDate.parse(date)
        val user = usersService.get(authentication.name)

        val report = appGroupService.getReport(user, day)

        val devices = devicesService.getAllDevices(user)
        // Compute each device's current final state once (avoid repeated fetches)
        val deviceFinalStates: Map<DeviceId, DeviceStateDto?> = devices.associate { device ->
            val details = device.getDetails()
            details.deviceId to stateService.calculateCurrentDeviceState(details).finalState
        }

        return AppGroupStatisticsResponse(
            groups = report.map { groupReport ->
                val appGroup = appGroupService.get(user, groupReport.id)
                val currentGroupState = calculateCurrentGroupState(appGroup, deviceFinalStates)
                AppGroupStatistics(
                    id = groupReport.id,
                    name = groupReport.name,
                    description = groupReport.description,
                    appsCount = groupReport.appsCount,
                    devicesCount = groupReport.devicesCount,
                    totalScreenTime = groupReport.totalScreenTime,
                    online = groupReport.online,
                    currentGroupState = currentGroupState,
                    apps = groupReport.apps.map {
                        AppGroupAppDetail(
                            name = it.name,
                            packageName = it.packageName,
                            deviceName = it.deviceName,
                            deviceId = it.deviceId,
                            screenTime = it.screenTime,
                            percentage = it.percentage,
                            iconBase64 = it.iconBase64,
                            online = it.online
                        )
                    }
                )
            }
        )
    }

    private fun calculateCurrentGroupState(appGroup: AppGroup, deviceFinalStates: Map<DeviceId, DeviceStateDto?>): CurrentGroupState {
        val groupStates = groupStateService.listAllGroupStates(appGroup)

        // Collect the device IDs referenced across all defined group states
        val groupDeviceIds = groupStates
            .flatMap { it.fetchDetails().deviceStates.keys }
            .toSet()

        if (groupDeviceIds.isEmpty()) {
            return CurrentGroupState(label = "Automatic", kind = "automatic")
        }

        // Try to match current device final states against a defined group state
        val matchedStateName = groupStates
            .map { it.fetchDetails() }
            .firstOrNull { details ->
                details.deviceStates.all { (deviceId, expectedState) ->
                    deviceFinalStates[deviceId] == expectedState
                }
            }
            ?.name

        if (matchedStateName != null) {
            return CurrentGroupState(label = matchedStateName, kind = "named")
        }

        // No named state matched — check if all referenced devices are running Automatic
        val allDevicesAutomatic = groupDeviceIds.all { deviceId ->
            val device = try { devicesService.get(deviceId) } catch (_: Exception) { return@all false }
            stateService.calculateCurrentDeviceState(device).forcedState == null
        }

        return if (allDevicesAutomatic) {
            CurrentGroupState(label = "Automatic", kind = "automatic")
        } else {
            CurrentGroupState(label = "Different", kind = "different")
        }
    }

    @GetMapping("/bff/app-groups/{groupId}/all-apps")
    fun getAllAppsForGroup(
        @PathVariable groupId: String,
        authentication: Authentication
    ): GetAllAppsForGroupResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        val devices = devicesService.getAllDevices(user)

        val deviceApps = devices.map { device ->
            val deviceDetails = device.getDetails()
            val appsInGroup = appGroup.getMembers(device)
            
            val apps = deviceDetails.knownApps.map { (packageName, appInfo) ->
                AppInGroupInfo(
                    packageName = packageName,
                    name = appInfo.appName,
                    iconBase64 = appInfo.iconBase64Png,
                    inGroup = packageName in appsInGroup
                )
            }.sortedBy { it.name.lowercase() }

            DeviceAppsInfo(
                deviceId = deviceDetails.deviceId.toString(),
                deviceName = deviceDetails.deviceName,
                apps = apps
            )
        }.filter { it.apps.isNotEmpty() }

        return GetAllAppsForGroupResponse(
            groupId = groupId,
            devices = deviceApps
        )
    }

    @PutMapping("/bff/app-groups/{groupId}/members")
    fun updateGroupMembers(
        @PathVariable groupId: String,
        @RequestBody request: UpdateGroupMembersRequest,
        authentication: Authentication
    ): UpdateGroupMembersResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)

        // Process each device's app changes in one read + one write per device
        request.devices.forEach { deviceUpdate ->
            val device = devicesService.get(UUID.fromString(deviceUpdate.deviceId))
            val current = appGroup.getMembers(device)
            val updated = (current + deviceUpdate.appsToAdd) - deviceUpdate.appsToRemove.toSet()
            if (updated != current) {
                appGroupRepository.setMembers(appGroup.asRef(), device.asRef(), updated)
            }
        }

        return UpdateGroupMembersResponse(success = true)
    }

}

data class CreateAppGroupRequest(
    val name: String
)

data class CreateAppGroupResponse(
    val group: AppGroupDetails
)

data class GetAppGroupsResponse(
    val groups: List<AppGroupDetails>
)

data class DeleteAppGroupResponse(
    val success: Boolean
)

data class UpdateAppGroupRequest(
    val newName: String,
    val description: String = "",
)

data class UpdateAppGroupResponse(
    val success: Boolean
)

data class RenameAppGroupRequest(
    val newName: String
)

data class RenameAppGroupResponse(
    val success: Boolean
)

data class AddAppToGroupRequest(
    val instanceId: UUID,
    val appPath: String
)

data class AddAppToGroupResponse(
    val success: Boolean
)

data class RemoveAppFromGroupResponse(
    val success: Boolean
)

data class AppGroupWithColor(
    val id: String,
    val name: String,
)

data class AppGroupStatistics(
    val id: String,
    val name: String,
    val description: String = "",
    val appsCount: Int,
    val devicesCount: Int,
    val totalScreenTime: Long,
    val online: Boolean = false,
    val currentGroupState: CurrentGroupState? = null,
    val apps: List<AppGroupAppDetail> = emptyList()
)

data class CurrentGroupState(
    val label: String,
    val kind: String, // "named" | "automatic" | "different"
)

data class AppGroupAppDetail(
    val name: String,
    val packageName: String,
    val deviceName: String,
    val deviceId: String,
    val screenTime: Long,
    val percentage: Double,
    val iconBase64: String? = null,
    val online: Boolean = false
)

data class AppGroupStatisticsResponse(
    val groups: List<AppGroupStatistics>
)

data class GetAllAppsForGroupResponse(
    val groupId: String,
    val devices: List<DeviceAppsInfo>
)

data class DeviceAppsInfo(
    val deviceId: String,
    val deviceName: String,
    val apps: List<AppInGroupInfo>
)

data class AppInGroupInfo(
    val packageName: String,
    val name: String,
    val iconBase64: String?,
    val inGroup: Boolean
)

data class UpdateGroupMembersRequest(
    val devices: List<DeviceGroupUpdate>
)

data class DeviceGroupUpdate(
    val deviceId: String,
    val appsToAdd: List<String>,
    val appsToRemove: List<String>
)

data class UpdateGroupMembersResponse(
    val success: Boolean
)
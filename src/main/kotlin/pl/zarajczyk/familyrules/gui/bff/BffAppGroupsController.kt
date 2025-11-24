package pl.zarajczyk.familyrules.gui.bff

import kotlinx.datetime.LocalDate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import pl.zarajczyk.familyrules.domain.AppGroupDetails
import pl.zarajczyk.familyrules.domain.AppGroupService
import pl.zarajczyk.familyrules.domain.DevicesService
import pl.zarajczyk.familyrules.domain.UsersService
import java.util.*

@RestController
class BffAppGroupsController(
    private val usersService: UsersService,
    private val appGroupService: AppGroupService,
    private val devicesService: DevicesService
) {

    @PostMapping("/bff/app-groups")
    fun createAppGroup(
        @RequestBody request: CreateAppGroupRequest,
        authentication: Authentication
    ): CreateAppGroupResponse {
        val user = usersService.get(authentication.name)
        val group = appGroupService.createAppGroup(user, request.name)
        return CreateAppGroupResponse(group.fetchDetails())
    }


    @GetMapping("/bff/app-groups")
    fun getAppGroups(authentication: Authentication): GetAppGroupsResponse {
        val user = usersService.get(authentication.name)
        val groups = appGroupService.listAllAppGroups(user).map { it.fetchDetails() }
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
    fun renameAppGroup(
        @PathVariable groupId: String,
        @RequestBody request: RenameAppGroupRequest,
        authentication: Authentication
    ): RenameAppGroupResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        appGroup.rename(request.newName)
        return RenameAppGroupResponse(true)
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

        val report = usersService.get(authentication.name).let { user ->
            appGroupService.getReport(user, day)
        }

        return AppGroupStatisticsResponse(
            groups = report.map {
                AppGroupStatistics(
                    id = it.id,
                    name = it.name,
                    color = it.color,
                    textColor = it.textColor,
                    appsCount = it.appsCount,
                    devicesCount = it.devicesCount,
                    totalScreenTime = it.totalScreenTime,
                    online = it.online,
                    apps = it.apps.map {
                        AppGroupAppDetail(
                            name = it.name,
                            packageName = it.packageName,
                            deviceName = it.deviceName,
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
    val color: String,
    val textColor: String,
)

data class AppGroupStatistics(
    val id: String,
    val name: String,
    val color: String,
    val textColor: String,
    val appsCount: Int,
    val devicesCount: Int,
    val totalScreenTime: Long,
    val online: Boolean = false,
    val apps: List<AppGroupAppDetail> = emptyList()
)

data class AppGroupAppDetail(
    val name: String,
    val packageName: String,
    val deviceName: String,
    val screenTime: Long,
    val percentage: Double,
    val iconBase64: String? = null,
    val online: Boolean = false
)

data class AppGroupStatisticsResponse(
    val groups: List<AppGroupStatistics>
)
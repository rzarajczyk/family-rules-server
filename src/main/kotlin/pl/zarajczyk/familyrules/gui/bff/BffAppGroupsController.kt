package pl.zarajczyk.familyrules.gui.bff

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import pl.zarajczyk.familyrules.domain.*
import java.util.*

@RestController
class BffAppGroupsController(
    private val usersService: UsersService,
    private val deviceStateService: DeviceStateService,
    private val appGroupService: AppGroupService,
    private val dataRepository: DataRepository
) {

    @PostMapping("/bff/app-groups")
    fun createAppGroup(
        @RequestBody request: CreateAppGroupRequest,
        authentication: Authentication
    ): CreateAppGroupResponse =
        usersService.withUserContext(authentication.name) { user ->
            val group = appGroupService.createAppGroup(user, request.name)
            CreateAppGroupResponse(group.get())
        }


    @GetMapping("/bff/app-groups")
    fun getAppGroups(authentication: Authentication): GetAppGroupsResponse =
        usersService.withUserContext(authentication.name) { user ->
            val groups = appGroupService.listAllAppGroups(user).map { it.get() }
            GetAppGroupsResponse(groups)
        }


    @DeleteMapping("/bff/app-groups/{groupId}")
    fun deleteAppGroup(
        @PathVariable groupId: String,
        authentication: Authentication
    ): DeleteAppGroupResponse =
        usersService.withUserContext(authentication.name) { user ->
            appGroupService.withAppGroupContext(user, groupId) { appGroup ->
                appGroup.delete()
                DeleteAppGroupResponse(true)
            }
        }


    @PutMapping("/bff/app-groups/{groupId}")
    fun renameAppGroup(
        @PathVariable groupId: String,
        @RequestBody request: RenameAppGroupRequest,
        authentication: Authentication
    ): RenameAppGroupResponse =
        usersService.withUserContext(authentication.name) { user ->
            appGroupService.withAppGroupContext(user, groupId) { appGroup ->
                appGroup.rename(request.newName)
                RenameAppGroupResponse(true)
            }
        }

    @PostMapping("/bff/app-groups/{groupId}/apps")
    fun addAppToGroup(
        @PathVariable groupId: String,
        @RequestBody request: AddAppToGroupRequest,
        authentication: Authentication
    ): AddAppToGroupResponse =
        usersService.withUserContext(authentication.name) { user ->
            appGroupService.withAppGroupContext(user, groupId) { appGroup ->
                val deviceRef = dataRepository.findDeviceOrThrow(request.instanceId)
                appGroup.addMember(deviceRef, request.appPath)
                AddAppToGroupResponse(true)
            }
        }

    @DeleteMapping("/bff/app-groups/{groupId}/apps/{appPath}")
    fun removeAppFromGroup(
        @PathVariable groupId: String,
        @PathVariable appPath: String,
        @RequestParam instanceId: UUID,
        authentication: Authentication
    ): RemoveAppFromGroupResponse =
        usersService.withUserContext(authentication.name) { user ->
            appGroupService.withAppGroupContext(user, groupId) { appGroup ->
                val deviceRef = dataRepository.findDeviceOrThrow(instanceId)
                appGroup.addMember(deviceRef, appPath)
                RemoveAppFromGroupResponse(true)
            }
        }


    @GetMapping("/bff/app-groups/statistics")
    fun getAppGroupStatistics(
        @RequestParam("date") date: String,
        authentication: Authentication
    ): AppGroupStatisticsResponse {
        val day = LocalDate.parse(date)
        val username = authentication.name

        val report = usersService.withUserContext(username) { user ->
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
                    apps = it.apps.map {
                        AppGroupAppDetail(
                            name = it.name,
                            packageName = it.packageName,
                            deviceName = it.deviceName,
                            screenTime = it.screenTime,
                            percentage = it.percentage,
                            iconBase64 = it.iconBase64
                        )
                    }
                )
            }
        )
    }


    private fun Instant.isOnline(reportIntervalSeconds: Int? = null) =
        (Clock.System.now() - this).inWholeSeconds <= (reportIntervalSeconds ?: 60)

    private fun DeviceStateTypeDto.toDeviceStateDescriptions(appGroups: List<AppGroupDto>) =
        deviceStateService.createActualInstances(this, appGroups)
            .map {
                DeviceStateDescriptionResponse(
                    deviceState = it.deviceState,
                    title = it.title,
                    icon = it.icon,
                    description = it.description,
                    extra = it.extra
                )
            }

}

private fun String?.emptyToNull(): String? = if (this.isNullOrBlank()) null else this

data class CreateAppGroupRequest(
    val name: String
)

data class CreateAppGroupResponse(
    val group: AppGroupDto
)

data class GetAppGroupsResponse(
    val groups: List<AppGroupDto>
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
    val apps: List<AppGroupAppDetail> = emptyList()
)

data class AppGroupAppDetail(
    val name: String,
    val packageName: String,
    val deviceName: String,
    val screenTime: Long,
    val percentage: Double,
    val iconBase64: String? = null
)

data class AppGroupStatisticsResponse(
    val groups: List<AppGroupStatistics>
)
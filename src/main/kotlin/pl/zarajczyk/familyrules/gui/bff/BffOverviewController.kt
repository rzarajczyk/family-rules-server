package pl.zarajczyk.familyrules.gui.bff

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.domain.*
import java.time.DayOfWeek
import java.util.*

@RestController
class BffOverviewController(
    private val dbConnector: DataRepository,
    private val scheduleUpdater: ScheduleUpdater,
    private val stateService: StateService,
    private val deviceStateService: DeviceStateService,
) {

//    companion object {
//        private val DEFAULT_ICON = Icon(
//            type = "image/png",
//            data = Base64.getEncoder().encodeToString(
//                BffOverviewController::class.java.getResourceAsStream("/gui/default-icon.png")!!.readAllBytes()
//            )
//        )
//    }

    @GetMapping("/bff/status")
    fun status(
        @RequestParam("date") date: String,
        authentication: Authentication,
    ): StatusResponse = try {
        val day = LocalDate.parse(date)
        val instances = dbConnector.findInstances(authentication.name)
        val username = authentication.name
        StatusResponse(instances.map { instanceRef ->
            val screenTimeDto = dbConnector.getScreenTimes(instanceRef, day)
            val state = stateService.getDeviceState(instanceRef)
            val instance = dbConnector.getInstance(instanceRef)
            val availableStates = dbConnector.getAvailableDeviceStateTypes(instanceRef)
            val appGroupMemberships = dbConnector.getAppGroupMemberships(instanceRef)
            val appGroups = dbConnector.getAppGroups(username)

            Instance(
                instanceId = instance.id,
                instanceName = instance.name,
                screenTimeSeconds = screenTimeDto.screenTimeSeconds,
                appUsageSeconds = screenTimeDto.applicationsSeconds
                    .map { (k, v) ->
                        val knownApp = instance.knownApps[k]
                        val appGroupsForThisApp = appGroupMemberships
                            .filter { it.appPath == k }
                            .mapNotNull { membership ->
                                appGroups.find { it.id == membership.groupId }
                            }
                            .map { group ->
                                val colorInfo = AppGroupColorPalette.getColorInfo(group.color)
                                AppGroupWithColor(
                                    id = group.id,
                                    name = group.name,
                                    color = group.color,
                                    textColor = colorInfo?.text ?: "#000000",
                                    createdAt = group.createdAt
                                )
                            }
                        AppUsage(
                            name = k,
                            path = k,
                            usageSeconds = v,
                            appName = knownApp?.appName,
                            iconBase64 = knownApp?.iconBase64Png,
                            appGroups = appGroupsForThisApp
                        )
                    }.sortedByDescending { it.usageSeconds },
                forcedDeviceState = availableStates
                    .flatMap { it.toDeviceStateDescriptions(appGroups) }
                    .firstOrNull { it.isEqualTo(state.forcedState) },
                automaticDeviceState = availableStates
                    .flatMap { it.toDeviceStateDescriptions(appGroups) }
                    .firstOrNull { it.isEqualTo(state.automaticState) }
                    ?: throw RuntimeException("Instance ≪${instance.id}≫ doesn't have automatic state ≪${state.automaticState}≫"),
                online = screenTimeDto.updatedAt.isOnline(instance.reportIntervalSeconds),
                icon = instance.getIcon(),
                availableAppGroups = appGroups,
                associatedAppGroupId = instance.associatedAppGroupId
            )
        })
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun DeviceStateDescriptionResponse.isEqualTo(other: DeviceStateDto?) =
        this.deviceState == other?.deviceState && this.extra == other?.extra

    private fun InstanceDto.getIcon() = if (iconType != null && iconData != null) {
        Icon(type = iconType, data = iconData)
    } else {
        Icon(type = null, data = null)
    }

    @GetMapping("/bff/instance-info")
    fun getInstanceInfo(
        @RequestParam("instanceId") instanceId: InstanceId
    ): InstanceInfoResponse {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        val instance = dbConnector.getInstance(instanceRef)
        return InstanceInfoResponse(
            instanceId = instanceId,
            instanceName = instance.name,
            forcedDeviceState = instance.forcedDeviceState,
            clientType = instance.clientType,
            clientVersion = instance.clientVersion,
            clientTimezoneOffsetSeconds = instance.clientTimezoneOffsetSeconds
        )
    }

    @GetMapping("/bff/instance-edit-info")
    fun getInstanceEditInfo(
        @RequestParam("instanceId") instanceId: InstanceId
    ): InstanceEditInfo {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        val instance = dbConnector.getInstance(instanceRef)
        return InstanceEditInfo(
            instanceName = instance.name,
            icon = instance.getIcon()
        )
    }

    @PostMapping("/bff/instance-edit-info")
    fun setInstanceEditInfo(
        @RequestParam("instanceId") instanceId: InstanceId,
        @RequestBody data: InstanceEditInfo
    ) {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        dbConnector.updateInstance(
            instanceRef, UpdateInstanceDto(
                instanceId = instanceId,
                name = data.instanceName,
                iconType = data.icon?.type,
                iconData = data.icon?.data
            )
        )
    }

    data class InstanceEditInfo(
        val instanceName: String,
        val icon: Icon?
    )

    @GetMapping("/bff/instance-schedule")
    fun getInstanceSchedule(
        @RequestParam("instanceId") instanceId: InstanceId,
        authentication: Authentication,
    ): ScheduleResponse {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        val instance = dbConnector.getInstance(instanceRef)
        val availableStates = dbConnector.getAvailableDeviceStateTypes(instanceRef)
        val appGroups = dbConnector.getAppGroups(authentication.name)
        return ScheduleResponse(
            schedules = instance.schedule.schedule
                .mapKeys { (day, _) -> day.toDay() }
                .mapValues { (_, periods) ->
                    DailySchedule(periods = periods.periods.map { period ->
                        Period(
                            from = period.fromSeconds.toRoundedTimeOfDay(zeroMeansStepBack = false),
                            to = period.toSeconds.toRoundedTimeOfDay(zeroMeansStepBack = true),
                            state = availableStates
                                .flatMap { it.toDeviceStateDescriptions(appGroups) }
                                .firstOrNull { it.isEqualTo(period.deviceState) }
                        )
                    })
                },
            availableStates = availableStates.flatMap { it.toDeviceStateDescriptions(appGroups) }
        )
    }

    private fun Day.toDayOfWeek() = when (this) {
        Day.MON -> DayOfWeek.MONDAY
        Day.TUE -> DayOfWeek.TUESDAY
        Day.WED -> DayOfWeek.WEDNESDAY
        Day.THU -> DayOfWeek.THURSDAY
        Day.FRI -> DayOfWeek.FRIDAY
        Day.SAT -> DayOfWeek.SATURDAY
        Day.SUN -> DayOfWeek.SUNDAY
    }


    private fun DayOfWeek.toDay() = when (this) {
        DayOfWeek.MONDAY -> Day.MON
        DayOfWeek.TUESDAY -> Day.TUE
        DayOfWeek.WEDNESDAY -> Day.WED
        DayOfWeek.THURSDAY -> Day.THU
        DayOfWeek.FRIDAY -> Day.FRI
        DayOfWeek.SATURDAY -> Day.SAT
        DayOfWeek.SUNDAY -> Day.SUN
    }


    @PostMapping("/bff/instance-schedule/add-period")
    fun addInstanceSchedulePeriod(
        @RequestParam("instanceId") instanceId: InstanceId,
        @RequestBody data: AddPeriodRequest
    ) {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        val instance = dbConnector.getInstance(instanceRef)
        val schedule = instance.schedule
        val period = PeriodDto(
            fromSeconds = (data.from.hour * 3600 + data.from.minute * 60).toLong(),
            toSeconds = (data.to.hour * 3600 + data.to.minute * 60).toLong(),
            deviceState = data.state
        )
        val updatedSchedule = data.days.fold(schedule) { currentSchedule, day ->
            scheduleUpdater.addPeriod(currentSchedule, day.toDayOfWeek(), period)
        }
        dbConnector.setInstanceSchedule(instanceRef, updatedSchedule)
    }

    data class AddPeriodRequest(
        val days: List<Day>,
        val from: TimeOfDay,
        val to: TimeOfDay,
        val state: DeviceStateDto,
    )

    private fun Long.toRoundedTimeOfDay(roundToMinutes: Int = 15, zeroMeansStepBack: Boolean): TimeOfDay {
        val SECONDS_IN_MINUTE = 60
        val SECONDS_IN_HOUR = 60 * SECONDS_IN_MINUTE

        val hour = (this / SECONDS_IN_HOUR).toInt()
        val minute = ((this % SECONDS_IN_HOUR) / SECONDS_IN_MINUTE).toInt()
        val roundedMinute = (minute / roundToMinutes) * roundToMinutes
        return TimeOfDay(hour, roundedMinute)
    }

    @GetMapping("/bff/instance-state")
    fun getInstanceState(
        @RequestParam("instanceId") instanceId: InstanceId,
        authentication: Authentication,
    ): InstanceStateResponse {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        val instance = dbConnector.getInstance(instanceRef)
        val appGroups = dbConnector.getAppGroups(authentication.name)
        return InstanceStateResponse(
            instanceId = instanceId,
            instanceName = instance.name,
            forcedDeviceState = instance.forcedDeviceState,
            availableStates = dbConnector.getAvailableDeviceStateTypes(instanceRef)
                .flatMap { it.toDeviceStateDescriptions(appGroups) }
        )
    }

    @PostMapping("/bff/instance-state")
    fun setInstanceState(
        @RequestParam("instanceId") instanceId: InstanceId,
        @RequestBody data: ForcedInstanceState
    ) {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        val forcedDeviceState = data.forcedDeviceState.emptyToNull()?.let {
            DeviceStateDto(
                deviceState = it,
                extra = data.extra?.emptyToNull()
            )
        }
        dbConnector.setForcedInstanceState(instanceRef, forcedDeviceState)
    }

    @PostMapping("/bff/delete-instance")
    fun deleteInstance(
        @RequestParam("instanceId") instanceId: InstanceId
    ) {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        dbConnector.deleteInstance(instanceRef)
    }

    @PostMapping("/bff/instance-associated-group")
    fun setInstanceAssociatedGroup(
        @RequestParam("instanceId") instanceId: InstanceId,
        @RequestBody request: SetAssociatedGroupRequest
    ): SetAssociatedGroupResponse {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        dbConnector.setAssociatedAppGroup(instanceRef, request.groupId)
        return SetAssociatedGroupResponse(success = true)
    }

    // App Groups BFF endpoints
    @PostMapping("/bff/app-groups")
    fun createAppGroup(
        @RequestBody request: CreateAppGroupRequest,
        authentication: Authentication
    ): CreateAppGroupResponse {
        val username = authentication.name
        val group = dbConnector.createAppGroup(username, request.name)
        return CreateAppGroupResponse(group)
    }

    @GetMapping("/bff/app-groups")
    fun getAppGroups(authentication: Authentication): GetAppGroupsResponse {
        val username = authentication.name
        val groups = dbConnector.getAppGroups(username)
        return GetAppGroupsResponse(groups)
    }

    @DeleteMapping("/bff/app-groups/{groupId}")
    fun deleteAppGroup(
        @PathVariable groupId: String,
        authentication: Authentication
    ): DeleteAppGroupResponse {
        val username = authentication.name
        dbConnector.deleteAppGroup(username, groupId)
        return DeleteAppGroupResponse(true)
    }

    @PutMapping("/bff/app-groups/{groupId}")
    fun renameAppGroup(
        @PathVariable groupId: String,
        @RequestBody request: RenameAppGroupRequest,
        authentication: Authentication
    ): RenameAppGroupResponse {
        val username = authentication.name
        val updatedGroup = dbConnector.renameAppGroup(username, groupId, request.newName)
        return RenameAppGroupResponse(updatedGroup)
    }

    @PostMapping("/bff/app-groups/{groupId}/apps")
    fun addAppToGroup(
        @PathVariable groupId: String,
        @RequestBody request: AddAppToGroupRequest,
        authentication: Authentication
    ): AddAppToGroupResponse {
        val username = authentication.name
        dbConnector.addAppToGroup(username, request.instanceId, request.appPath, groupId)
        return AddAppToGroupResponse(true)
    }

    @DeleteMapping("/bff/app-groups/{groupId}/apps/{appPath}")
    fun removeAppFromGroup(
        @PathVariable groupId: String,
        @PathVariable appPath: String,
        @RequestParam instanceId: UUID,
        authentication: Authentication
    ): RemoveAppFromGroupResponse {
        val username = authentication.name
        dbConnector.removeAppFromGroup(username, instanceId, appPath, groupId)
        return RemoveAppFromGroupResponse(true)
    }

    @GetMapping("/bff/app-groups/statistics")
    fun getAppGroupStatistics(
        @RequestParam("date") date: String,
        authentication: Authentication
    ): AppGroupStatisticsResponse {
        val day = LocalDate.parse(date)
        val username = authentication.name
        val instances = dbConnector.findInstances(username)
        val appGroups = dbConnector.getAppGroups(username)

        val groupStats = appGroups.map { group ->
            // Calculate statistics across all instances
            var totalApps = 0
            var totalScreenTime = 0L
            val deviceCount = mutableSetOf<String>()
            val appDetails = mutableListOf<AppGroupAppDetail>()

            instances.forEach { instanceRef ->
                val instance = dbConnector.getInstance(instanceRef)
                val screenTimeDto = dbConnector.getScreenTimes(instanceRef, day)
                val instanceMemberships = dbConnector.getAppGroupMemberships(instanceRef)
                    .filter { it.groupId == group.id }

                if (instanceMemberships.isNotEmpty()) {
                    deviceCount.add(instance.id.toString())
                    totalApps += instanceMemberships.size

                    // Collect detailed app information for this group
                    instanceMemberships.forEach { membership ->
                        val appScreenTime = screenTimeDto.applicationsSeconds[membership.appPath] ?: 0L
                        totalScreenTime += appScreenTime

                        // Get app name and icon from known apps or use the path
                        val knownApp = instance.knownApps[membership.appPath]
                        val appName = knownApp?.appName ?: membership.appPath
                        val appIcon = knownApp?.iconBase64Png

                        appDetails.add(
                            AppGroupAppDetail(
                                name = appName,
                                packageName = membership.appPath,
                                deviceName = instance.name,
                                screenTime = appScreenTime,
                                percentage = 0.0, // Will be calculated below
                                iconBase64 = appIcon
                            )
                        )
                    }
                }
            }

            // Calculate percentages for each app
            val appsWithPercentages = if (totalScreenTime > 0) {
                appDetails.map { app ->
                    app.copy(percentage = (app.screenTime.toDouble() / totalScreenTime * 100).let {
                        (it * 100).toInt().toDouble() / 100 // Round to 2 decimal places
                    })
                }.sortedByDescending { it.screenTime }
            } else {
                appDetails.sortedByDescending { it.screenTime }
            }

            val colorInfo = AppGroupColorPalette.getColorInfo(group.color)
            AppGroupStatistics(
                id = group.id,
                name = group.name,
                color = group.color,
                textColor = colorInfo?.text ?: "#000000",
                appsCount = totalApps,
                devicesCount = deviceCount.size,
                totalScreenTime = totalScreenTime,
                apps = appsWithPercentages
            )
        }

        return AppGroupStatisticsResponse(groupStats)
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

data class InstanceStateResponse(
    val instanceId: InstanceId,
    val instanceName: String,
    val forcedDeviceState: DeviceStateDto?,
    val availableStates: List<DeviceStateDescriptionResponse>
)

data class InstanceInfoResponse(
    val instanceId: InstanceId,
    val instanceName: String,
    val forcedDeviceState: DeviceStateDto?,
    val clientType: String,
    val clientVersion: String,
    val clientTimezoneOffsetSeconds: Int,
)

data class DeviceStateDescriptionResponse(
    val deviceState: String,
    val title: String,
    val icon: String?,
    val description: String?,
    val extra: String?
)


data class StatusResponse(
    val instances: List<Instance>
)

data class Instance(
    val instanceId: InstanceId,
    val instanceName: String,
    val icon: Icon,
    val screenTimeSeconds: Long,
    val appUsageSeconds: List<AppUsage>,
    val automaticDeviceState: DeviceStateDescriptionResponse,
    val forcedDeviceState: DeviceStateDescriptionResponse?,
    val online: Boolean,
    val availableAppGroups: List<AppGroupDto>,
    val associatedAppGroupId: String? = null
)

data class Icon(
    val type: String?,
    val data: String?
)

data class AppUsage(
    val name: String,
    val path: String,
    val usageSeconds: Long,
    val appName: String? = null,
    val iconBase64: String? = null,
    val appGroups: List<AppGroupWithColor> = emptyList()
)

data class ForcedInstanceState(
    val forcedDeviceState: String?,
    val extra: String?
)

data class ScheduleResponse(
    val schedules: Map<Day, DailySchedule>,
    val availableStates: List<DeviceStateDescriptionResponse>
)

data class DailySchedule(
    val periods: List<Period>
)

data class Period(
    val from: TimeOfDay,
    val to: TimeOfDay,
    val state: DeviceStateDescriptionResponse?,
)

data class TimeOfDay(
    val hour: Int,
    val minute: Int
)

enum class Day {
    MON, TUE, WED, THU, FRI, SAT, SUN
}

// App Groups BFF data classes
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
    val group: AppGroupDto
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
    val createdAt: Instant
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

data class SetAssociatedGroupRequest(
    val groupId: String?
)

data class SetAssociatedGroupResponse(
    val success: Boolean
)
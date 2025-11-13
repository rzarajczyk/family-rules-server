package pl.zarajczyk.familyrules.gui.bff

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.*
import java.time.DayOfWeek

@RestController
class BffOverviewController(
    private val dbConnector: DevicesRepository,
    private val devicesService: DevicesService,
    private val scheduleUpdater: ScheduleUpdater,
    private val stateService: StateService,
    private val deviceStateService: DeviceStateService,
    private val appGroupService: AppGroupService,
    private val usersService: UsersService
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
        val instances = dbConnector.getAll(authentication.name)
        val username = authentication.name
        StatusResponse(instances.map { deviceRef ->
            val screenTimeDto = dbConnector.getScreenTimes(deviceRef, day)
            val state = stateService.getDeviceState(deviceRef)
            val instance = dbConnector.fetchDetails(deviceRef)
            val availableStates = dbConnector.getAvailableDeviceStateTypes(deviceRef)
            val appGroups = usersService.withUserContext(username) { user ->
                appGroupService.listAllAppGroups(user)
            }
            val appGroupsDetails = appGroups.associateWith { it.get() }

            Instance(
                instanceId = instance.deviceId,
                instanceName = instance.deviceName,
                screenTimeSeconds = screenTimeDto.screenTimeSeconds,
                appUsageSeconds = screenTimeDto.applicationsSeconds
                    .map { (appTechnicalId, v) ->
                        val knownApp = instance.knownApps[appTechnicalId]
                        val appGroupsForThisApp = appGroups
                            .filter { it.containsMember(deviceRef, appTechnicalId) }
                            .map { group -> appGroupsDetails.getValue(group) }
                            .map { groupDto ->
                                val colorInfo = AppGroupColorPalette.getColorInfo(groupDto.color)
                                AppGroupWithColor(
                                    id = groupDto.id,
                                    name = groupDto.name,
                                    color = groupDto.color,
                                    textColor = colorInfo?.text ?: "#000000",
                                )
                            }
                        AppUsage(
                            name = appTechnicalId,
                            path = appTechnicalId,
                            usageSeconds = v,
                            appName = knownApp?.appName,
                            iconBase64 = knownApp?.iconBase64Png,
                            appGroups = appGroupsForThisApp
                        )
                    }.sortedByDescending { it.usageSeconds },
                forcedDeviceState = availableStates
                    .flatMap { it.toDeviceStateDescriptions(appGroupsDetails.values) }
                    .firstOrNull { it.isEqualTo(state.forcedState) },
                automaticDeviceState = availableStates
                    .flatMap { it.toDeviceStateDescriptions(appGroupsDetails.values) }
                    .firstOrNull { it.isEqualTo(state.automaticState) }
                    ?: throw RuntimeException("Instance ≪${instance.deviceId}≫ doesn't have automatic state ≪${state.automaticState}≫"),
                online = screenTimeDto.updatedAt.isOnline(instance.reportIntervalSeconds),
                icon = instance.getIcon(),
                availableAppGroups = appGroupsDetails.values.toList(),
            )
        })
    } catch (_: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun DeviceStateDescriptionResponse.isEqualTo(other: DeviceStateDto?) =
        this.deviceState == other?.deviceState && this.extra == other?.extra

    private fun DeviceDetailsDto.getIcon() = if (iconType != null && iconData != null) {
        Icon(type = iconType, data = iconData)
    } else {
        Icon(type = null, data = null)
    }

    private fun DeviceDto.getIcon() = if (iconType != null && iconData != null) {
        Icon(type = iconType, data = iconData)
    } else {
        Icon(type = null, data = null)
    }

    @GetMapping("/bff/instance-info")
    fun getInstanceInfo(
        @RequestParam("instanceId") instanceId: InstanceId
    ): InstanceInfoResponse {
        val instanceRef = dbConnector.findDeviceOrThrow(instanceId)
        val instance = dbConnector.fetchDetails(instanceRef)
        return InstanceInfoResponse(
            instanceId = instanceId,
            instanceName = instance.deviceName,
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
        val instanceRef = dbConnector.findDeviceOrThrow(instanceId)
        val instance = dbConnector.fetchDetails(instanceRef)
        return InstanceEditInfo(
            instanceName = instance.deviceName,
            icon = instance.getIcon()
        )
    }

    @PostMapping("/bff/instance-edit-info")
    fun setInstanceEditInfo(
        @RequestParam("instanceId") instanceId: InstanceId,
        @RequestBody data: InstanceEditInfo
    ) = devicesService.withDeviceContext(instanceId) { device ->
        device.update(
            DeviceDetailsUpdateDto(
                deviceName = ValueUpdate.set(data.instanceName),
            iconData = data.icon?.let { ValueUpdate.set(data.icon.data) } ?: ValueUpdate.leaveUnchanged(),
            iconType = data.icon?.let { ValueUpdate.set(data.icon.type) } ?: ValueUpdate.leaveUnchanged()
        ))
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
        val instanceRef = dbConnector.findDeviceOrThrow(instanceId)
        val instance = dbConnector.fetchDetails(instanceRef)
        val availableStates = dbConnector.getAvailableDeviceStateTypes(instanceRef)
        val appGroups = usersService.withUserContext(authentication.name) { user ->
            appGroupService.listAllAppGroups(user).map { it.get() }
        }
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
        val instanceRef = dbConnector.findDeviceOrThrow(instanceId)
        val instance = dbConnector.fetchDetails(instanceRef)
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
        val instanceRef = dbConnector.findDeviceOrThrow(instanceId)
        val instance = dbConnector.fetchDetails(instanceRef)
        val appGroups = usersService.withUserContext(authentication.name) { user ->
            appGroupService.listAllAppGroups(user).map { it.get() }
        }
        return InstanceStateResponse(
            instanceId = instanceId,
            instanceName = instance.deviceName,
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
        val instanceRef = dbConnector.findDeviceOrThrow(instanceId)
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
    ) = devicesService.withDeviceContext(instanceId) { device ->
        device.delete()
    }

    private fun Instant.isOnline(reportIntervalSeconds: Long) =
        (Clock.System.now() - this).inWholeSeconds <= reportIntervalSeconds

    private fun DeviceStateTypeDto.toDeviceStateDescriptions(appGroups: Collection<AppGroupDetails>) =
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
    val clientTimezoneOffsetSeconds: Long,
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
    val availableAppGroups: List<AppGroupDetails>,
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

data class SetAssociatedGroupRequest(
    val groupId: String?
)

data class SetAssociatedGroupResponse(
    val success: Boolean
)
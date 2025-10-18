package pl.zarajczyk.familyrules.gui.bff

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.shared.*
import java.time.DayOfWeek
import java.util.Base64

@RestController
class BffOverviewController(
    private val dbConnector: DataRepository,
    private val scheduleUpdater: ScheduleUpdater,
    private val stateService: StateService
) {

    companion object {
        private val DEFAULT_ICON = Icon(
            type = "image/png",
            data = Base64.getEncoder().encodeToString(
                BffOverviewController::class.java.getResourceAsStream("/gui/default-icon.png")!!.readAllBytes()
            )
        )
    }

    @GetMapping("/bff/status")
    fun status(
        @RequestParam("date") date: String,
        authentication: Authentication,
    ): StatusResponse = try {
        val day = LocalDate.parse(date)
        val instances = dbConnector.findInstances(authentication.name)
        StatusResponse(instances.map { instanceRef ->
            val screenTimeDto = dbConnector.getScreenTimes(instanceRef, day)
            val state = stateService.getDeviceState(instanceRef)
            val instance = dbConnector.getInstance(instanceRef)
            val availableStates = dbConnector.getAvailableDeviceStates(instanceRef)
            Instance(
                instanceId = instance.id,
                instanceName = instance.name,
                screenTimeSeconds = screenTimeDto.screenTimeSeconds,
                appUsageSeconds = screenTimeDto.applicationsSeconds
                    .map { (k, v) -> 
                        val knownApp = instance.knownApps[k]
                        AppUsage(
                            name = k,
                            path = k,
                            usageSeconds = v,
                            appName = knownApp?.appName,
                            iconBase64 = knownApp?.iconBase64Png
                        )
                    },
                forcedDeviceState = availableStates
                    .firstOrNull { it.deviceState == state.forcedState }
                    ?.toDeviceStateDescription(),
                automaticDeviceState = availableStates
                    .firstOrNull { it.deviceState == state.automaticState }
                    ?.toDeviceStateDescription()
                    ?: throw RuntimeException("Instance ≪${instance.id}≫ doesn't have automatic state ≪${state.automaticState}≫"),
                online = screenTimeDto.updatedAt.isOnline(instance.reportIntervalSeconds),
                icon = instance.getIcon()
            )
        })
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

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
        @RequestParam("instanceId") instanceId: InstanceId
    ): ScheduleResponse {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        val instance = dbConnector.getInstance(instanceRef)
        val availableStates = dbConnector.getAvailableDeviceStates(instanceRef)
        return ScheduleResponse(
            schedules = instance.schedule.schedule
                .mapKeys { (day, _) -> day.toDay() }
                .mapValues { (_, periods) ->
                    DailySchedule(periods = periods.periods.map { period ->
                        Period(
                            from = period.fromSeconds.toRoundedTimeOfDay(zeroMeansStepBack = false),
                            to = period.toSeconds.toRoundedTimeOfDay(zeroMeansStepBack = true),
                            state = availableStates.firstOrNull { it.deviceState == period.deviceState }
                                ?.toDeviceStateDescription(),
                        )
                    })
                },
            availableStates = availableStates.map { it.toDeviceStateDescription() }
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
        val state: DeviceState,
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
        @RequestParam("instanceId") instanceId: InstanceId
    ): InstanceStateResponse {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        val instance = dbConnector.getInstance(instanceRef)
        return InstanceStateResponse(
            instanceId = instanceId,
            instanceName = instance.name,
            forcedDeviceState = instance.forcedDeviceState,
            availableStates = dbConnector.getAvailableDeviceStates(instanceRef).map { it.toDeviceStateDescription() }
        )
    }

    @PostMapping("/bff/instance-state")
    fun setInstanceState(
        @RequestParam("instanceId") instanceId: InstanceId,
        @RequestBody data: InstanceState
    ) {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        dbConnector.setForcedInstanceState(instanceRef, data.forcedDeviceState.emptyToNull())
    }

    @PostMapping("/bff/delete-instance")
    fun deleteInstance(
        @RequestParam("instanceId") instanceId: InstanceId
    ) {
        val instanceRef = dbConnector.findInstanceOrThrow(instanceId)
        dbConnector.deleteInstance(instanceRef)
    }


    private fun Instant.isOnline(reportIntervalSeconds: Int? = null) = (Clock.System.now() - this).inWholeSeconds <= (reportIntervalSeconds ?: 60)

    private fun DescriptiveDeviceStateDto.toDeviceStateDescription() = DeviceStateDescription(
        deviceState = deviceState,
        title = title,
        icon = icon,
        description = description
    )

}

private fun DeviceState?.emptyToNull(): DeviceState? = if (this.isNullOrBlank()) null else this

data class InstanceStateResponse(
    val instanceId: InstanceId,
    val instanceName: String,
    val forcedDeviceState: DeviceState?,
    val availableStates: List<DeviceStateDescription>
)

data class InstanceInfoResponse(
    val instanceId: InstanceId,
    val instanceName: String,
    val forcedDeviceState: DeviceState?,
    val clientType: String,
    val clientVersion: String,
    val clientTimezoneOffsetSeconds: Int,
    )

data class DeviceStateDescription(
    val deviceState: DeviceState,
    val title: String,
    val icon: String?,
    val description: String?
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
    val automaticDeviceState: DeviceStateDescription,
    val forcedDeviceState: DeviceStateDescription?,
    val online: Boolean
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
    val iconBase64: String? = null
)

data class InstanceState(
    val forcedDeviceState: DeviceState?
)

data class ScheduleResponse(
    val schedules: Map<Day, DailySchedule>,
    val availableStates: List<DeviceStateDescription>
)

data class DailySchedule(
    val periods: List<Period>
)

data class Period(
    val from: TimeOfDay,
    val to: TimeOfDay,
    val state: DeviceStateDescription?,
)

data class TimeOfDay(
    val hour: Int,
    val minute: Int
)

enum class Day {
    MON, TUE, WED, THU, FRI, SAT, SUN
}
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
    private val dbConnector: DbConnector,
    private val scheduleUpdater: ScheduleUpdater,
    private val stateService: StateService
) {

    companion object {
        private val DEFAULT_ICON = Icon(
            type = "image/png",
            data = Base64.getEncoder().encodeToString(
                BffOverviewController::class.java.getResourceAsStream("/default-icon.png")!!.readAllBytes()
            )
        )
    }

    @GetMapping("/bff/status")
    fun status(
        @RequestParam("date") date: String,
        authentication: Authentication,
    ): StatusResponse = try {
        val day = LocalDate.parse(date)
        val instances = dbConnector.getInstances(authentication.name)
        StatusResponse(instances.map { instance ->
            val appUsageMap = dbConnector.getScreenTimes(instance.id, day)
            val state = stateService.getDeviceState(instance)
            Instance(
                instanceId = instance.id,
                instanceName = instance.name,
                screenTimeSeconds = appUsageMap[DbConnector.TOTAL_TIME]?.screenTimeSeconds ?: 0L,
                appUsageSeconds = (appUsageMap - DbConnector.TOTAL_TIME).map { (k, v) ->
                    AppUsage(
                        k,
                        v.screenTimeSeconds
                    )
                },
                forcedDeviceState = dbConnector.getAvailableDeviceStates(instance.id)
                    .firstOrNull { it.deviceState == state.forcedState }
                    ?.toDeviceStateDescription(),
                automaticDeviceState = dbConnector.getAvailableDeviceStates(instance.id)
                    .firstOrNull { it.deviceState == state.automaticState }
                    ?.toDeviceStateDescription()
                    ?: throw RuntimeException("Instance ≪${instance.name}≫ doesn't have automatic state ≪${state.automaticState}≫"),
                online = appUsageMap.maxOfOrNull { (_, v) -> v.updatedAt }?.isOnline() ?: false,
                icon = instance.getIcon()
            )
        })
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun InstanceDto.getIcon() = if (iconType != null && iconData != null) {
        Icon(type = iconType, data = iconData)
    } else {
        DEFAULT_ICON
    }

    @GetMapping("/bff/instance-info")
    fun getInstanceInfo(
        @RequestParam("instanceId") instanceId: InstanceId
    ): InstanceInfoResponse {
        val instance = dbConnector.getInstance(instanceId) ?: throw RuntimeException("Instance not found $instanceId")
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
        val instance = dbConnector.getInstance(instanceId) ?: throw RuntimeException("Instance not found $instanceId")
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
        dbConnector.updateInstance(
            instanceId, UpdateInstanceDto(
                instanceId = instanceId,
                name = data.instanceName,
                iconType = data.icon.type,
                iconData = data.icon.data
            )
        )
    }

    data class InstanceEditInfo(
        val instanceName: String,
        val icon: Icon
    )

    @GetMapping("/bff/instance-schedule")
    fun getInstanceSchedule(
        @RequestParam("instanceId") instanceId: InstanceId
    ): ScheduleResponse {
        val instance = dbConnector.getInstance(instanceId) ?: throw RuntimeException("Instance not found $instanceId")
        val availableStates = dbConnector.getAvailableDeviceStates(instanceId)
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
        val instance = dbConnector.getInstance(instanceId) ?: throw RuntimeException("Instance not found $instanceId")
        val schedule = instance.schedule
        val period = PeriodDto(
            fromSeconds = (data.from.hour * 3600 + data.from.minute * 60).toLong(),
            toSeconds = (data.to.hour * 3600 + data.to.minute * 60).toLong(),
            deviceState = data.state
        )
        val updatedSchedule = data.days.fold(schedule) { currentSchedule, day ->
            scheduleUpdater.addPeriod(currentSchedule, day.toDayOfWeek(), period)
        }
        dbConnector.setInstanceSchedule(instanceId, updatedSchedule)
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
        val instance = dbConnector.getInstance(instanceId) ?: throw RuntimeException("Instance not found $instanceId")
        return InstanceStateResponse(
            instanceId = instanceId,
            instanceName = instance.name,
            forcedDeviceState = instance.forcedDeviceState,
            availableStates = dbConnector.getAvailableDeviceStates(instanceId).map { it.toDeviceStateDescription() }
        )
    }

    @PostMapping("/bff/instance-state")
    fun setInstanceState(
        @RequestParam("instanceId") instanceId: InstanceId,
        @RequestBody data: InstanceState
    ) {
        dbConnector.setForcedInstanceState(instanceId, data.forcedDeviceState.emptyToNull())
    }

    @PostMapping("/bff/delete-instance")
    fun deleteInstance(
        @RequestParam("instanceId") instanceId: InstanceId
    ) {
        dbConnector.deleteInstance(instanceId)
    }


    private fun Instant.isOnline() = (Clock.System.now() - this).inWholeSeconds <= 30

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
    val type: String,
    val data: String
)

data class AppUsage(
    val name: String,
    val usageSeconds: Long
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
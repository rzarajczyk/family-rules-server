package pl.zarajczyk.familyrules.gui.bff

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.shared.*
import java.time.LocalTime

@RestController
class BffOverviewController(private val dbConnector: DbConnector) {

    @GetMapping("/bff/status")
    fun status(
        @RequestParam("date") date: String,
        @RequestHeader("x-seed") seed: String,
        @RequestHeader("Authorization") authHeader: String
    ): StatusResponse = try {
        val day = LocalDate.parse(date)
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)

        val instances = dbConnector.getInstances(auth.user)
        StatusResponse(instances.map { instance ->
            val appUsageMap = dbConnector.getScreenTimes(instance.id, day)
            Instance(
                instanceId = instance.id,
                instanceName = instance.name,
                screenTimeSeconds = appUsageMap[DbConnector.TOTAL_TIME]?.screenTimeSeconds ?: 0L,
                appUsageSeconds = (appUsageMap - DbConnector.TOTAL_TIME).map { (k, v) -> AppUsage(k, v.screenTimeSeconds) },
                forcedDeviceState = dbConnector.getAvailableDeviceStates(instance.id)
                    .firstOrNull { it.deviceState == instance.forcedDeviceState }
                    ?.toDeviceStateDescription(),
                online = appUsageMap.maxOfOrNull { (_,v) -> v.updatedAt }?.isOnline() ?: false
//                weeklySchedule = dbConnector.getInstanceSchedule(it.id).toWeeklySchedule()
            )
        })
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    @GetMapping("/bff/instance-info")
    fun getInstanceInfo(
        @RequestParam("instanceId") instanceId: InstanceId,
        @RequestHeader("x-seed") seed: String,
        @RequestHeader("Authorization") authHeader: String
    ): InstanceInfoResponse {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)

        val instance = dbConnector.getInstance(instanceId) ?: throw RuntimeException("Instance not found $instanceId")
        return InstanceInfoResponse(
            instanceId = instanceId,
            instanceName = instance.name,
            forcedDeviceState = instance.forcedDeviceState,
            clientType = instance.clientType,
            clientVersion = instance.clientVersion
        )
    }

    @GetMapping("/bff/instance-schedule")
    fun getInstanceSchedule(
        @RequestParam("instanceId") instanceId: InstanceId,
        @RequestHeader("x-seed") seed: String,
        @RequestHeader("Authorization") authHeader: String
    ): ScheduleResponse {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)

        val instance = dbConnector.getInstance(instanceId) ?: throw RuntimeException("Instance not found $instanceId")
        return ScheduleResponse(
            schedules = instance.scheduleDto.schedule.mapValues { (_, periods) ->
                DailySchedule(periods = periods.periods.map { period ->
                    Period(
                        from = LocalTime.ofSecondOfDay(period.fromSeconds),
                        to = LocalTime.ofSecondOfDay(period.toSeconds),
                        state = period.deviceState
                    )
                })
            }
        )
    }

    @GetMapping("/bff/instance-state")
    fun getInstanceState(
        @RequestParam("instanceId") instanceId: InstanceId,
        @RequestHeader("x-seed") seed: String,
        @RequestHeader("Authorization") authHeader: String
    ): InstanceStateResponse {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)

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
        @RequestHeader("x-seed") seed: String,
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody data: InstanceState
    ) {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)

        dbConnector.setForcedInstanceState(instanceId, data.forcedDeviceState.emptyToNull())
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
    val clientVersion: String
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
    val screenTimeSeconds: Long,
    val appUsageSeconds: List<AppUsage>,
    val forcedDeviceState: DeviceStateDescription?,
    val online: Boolean
)

data class AppUsage(
    val name: String,
    val usageSeconds: Long
)

data class InstanceState(
    val forcedDeviceState: DeviceState?
)

data class ScheduleResponse(
    val schedules: Map<Day, DailySchedule>
)

data class DailySchedule(
    val periods: List<Period>
)

data class Period(
    val from: LocalTime,
    val to: LocalTime,
    val state: DeviceState
)
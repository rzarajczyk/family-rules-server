package pl.zarajczyk.familyrules.gui

import kotlinx.datetime.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.shared.*
import java.time.LocalTime

@RestController
class BffController(private val dbConnector: DbConnector) {

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
        StatusResponse(instances.map {
            val appUsageMap = dbConnector.getScreenTimeSeconds(it.id, day)
            Instance(
                instanceId = it.id,
                instanceName = it.name,
                screenTimeSeconds = appUsageMap.getOrDefault(DbConnector.TOTAL_TIME, 0L),
                appUsageSeconds = (appUsageMap - DbConnector.TOTAL_TIME).map { (k, v) -> AppUsage(k, v) },
                state = dbConnector.getInstanceState(it.id)?.toInstanceState() ?: InstanceState.empty(),
                weeklySchedule = dbConnector.getInstanceSchedule(it.id).toWeeklySchedule()
            )
        })
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    @PostMapping("/bff/state")
    fun state(
        @RequestHeader("x-seed") seed: String,
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam("instanceName") instanceName: String,
        @RequestBody state: InstanceState
    ) {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)
        val instanceId = dbConnector.getInstances(auth.user).find { it.name == instanceName }?.id
        if (instanceId == null)
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        dbConnector.setInstanceState(instanceId, state.toStateDto())
    }

    @PostMapping("/bff/schedule")
    fun schedule(
        @RequestHeader("x-seed") seed: String,
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam("instanceName") instanceName: String,
        @RequestBody weeklySchedule: WeeklySchedule
    ) {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)
        val instanceId = dbConnector.getInstances(auth.user).find { it.name == instanceName }?.id
        if (instanceId == null)
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        dbConnector.setInstanceSchedule(instanceId, weeklySchedule.toScheduleDto())
    }

    private fun StateDto.toInstanceState() = InstanceState(
        deviceState = this.deviceState,
        deviceStateCountdown = this.deviceStateCountdown
    )

    private fun InstanceState.toStateDto() = StateDto(
        deviceState = this.deviceState,
        deviceStateCountdown = this.deviceStateCountdown
    )

    private fun ScheduleDto.toWeeklySchedule(): WeeklySchedule = WeeklySchedule(
        schedules = schedule
            .mapValues { (_, periods) ->
                DailySchedule(
                    periods = periods.periods.map { dto ->
                        Period(
                            from = LocalTime.ofSecondOfDay(dto.fromSeconds),
                            to = LocalTime.ofSecondOfDay(dto.toSeconds),
                            state = dto.deviceState
                        )
                    }
                )
            }.ensureAllKeysExist()
    )

    private fun WeeklySchedule.toScheduleDto() = ScheduleDto(
        schedule = this.schedules.mapValues { (_, dailySchedule) ->
            PeriodsDto(periods = dailySchedule.periods.map { period ->
                PeriodDto(
                    fromSeconds = period.from.toSecondOfDay().toLong(),
                    toSeconds = period.to.toSecondOfDay().toLong(),
                    deviceState = period.state,
                    deviceStateCountdown = 0
                )
            })
        }
    )

    private fun Map<Day, DailySchedule>.ensureAllKeysExist(): Map<Day, DailySchedule> =
        Day.entries.associateWith { this[it] ?: DailySchedule(periods = emptyList()) }

}


data class StatusResponse(
    val instances: List<Instance>
)

data class Instance(
    val instanceId: InstanceId,
    val instanceName: String,
    val screenTimeSeconds: Long,
    val appUsageSeconds: List<AppUsage>,
    val state: InstanceState,
    val weeklySchedule: WeeklySchedule
)

data class AppUsage(
    val name: String,
    val usageSeconds: Long
)

data class InstanceState(
    val deviceState: DeviceState,
    val deviceStateCountdown: Int
) {
    companion object {
        fun empty() = InstanceState(DeviceState.ACTIVE, 0)
    }
}

data class WeeklySchedule(
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
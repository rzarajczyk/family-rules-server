package pl.zarajczyk.familyrules.gui.bff

import kotlinx.datetime.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.shared.*

@RestController
class BffOverviewController(private val dbConnector: DbConnector) {

    companion object {
        val AVAILABLE_STATES = listOf(
            DeviceStateDescription(
                deviceState = "ACTIVE",
                title = "Active",
                icon = "<path d=\"m424-296 282-282-56-56-226 226-114-114-56 56 170 170Zm56 216q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z\"/>",
                description = null
            ),
            DeviceStateDescription(
                deviceState = "LOCKED",
                title = "Locked",
                icon = "<path d=\"M240-80q-33 0-56.5-23.5T160-160v-400q0-33 23.5-56.5T240-640h40v-80q0-83 58.5-141.5T480-920q83 0 141.5 58.5T680-720v80h40q33 0 56.5 23.5T800-560v400q0 33-23.5 56.5T720-80H240Zm0-80h480v-400H240v400Zm240-120q33 0 56.5-23.5T560-360q0-33-23.5-56.5T480-440q-33 0-56.5 23.5T400-360q0 33 23.5 56.5T480-280ZM360-640h240v-80q0-50-35-85t-85-35q-50 0-85 35t-35 85v80ZM240-160v-400 400Z\"/>",
                description = null
            ),
            DeviceStateDescription(
                deviceState = "LOGGED_OUT",
                title = "Logged out",
                icon = "<path d=\"M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h280v80H200v560h280v80H200Zm440-160-55-58 102-102H360v-80h327L585-622l55-58 200 200-200 200Z\"/>",
                description = null
            ),
            DeviceStateDescription(
                deviceState = "APP_DISABLED",
                title = "App disabled",
                icon = "<path d=\"m40-120 440-760 440 760H40Zm138-80h604L480-720 178-200Zm302-40q17 0 28.5-11.5T520-280q0-17-11.5-28.5T480-320q-17 0-28.5 11.5T440-280q0 17 11.5 28.5T480-240Zm-40-120h80v-200h-80v200Zm40-100Z\"/>",
                description = """
                        If you select this state, the app on child's device will be turned off and removed from the "Autorun"/"Autostart".
                        Use this state if you want to update the app.
                        Remember to manually turn the app on after de-selecting this state.
                    """.trimIndent()
            ),
        )
    }

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
            val appUsageMap = dbConnector.getScreenTimeSeconds(instance.id, day)
            Instance(
                instanceId = instance.id,
                instanceName = instance.name,
                screenTimeSeconds = appUsageMap.getOrDefault(DbConnector.TOTAL_TIME, 0L),
                appUsageSeconds = (appUsageMap - DbConnector.TOTAL_TIME).map { (k, v) -> AppUsage(k, v) },
                forcedDeviceState = AVAILABLE_STATES.firstOrNull { it.deviceState == instance.forcedDeviceState },
//                weeklySchedule = dbConnector.getInstanceSchedule(it.id).toWeeklySchedule()
            )
        })
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
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
            availableStates = AVAILABLE_STATES
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

//    @PostMapping("/bff/state")
//    fun state(
//        @RequestHeader("x-seed") seed: String,
//        @RequestHeader("Authorization") authHeader: String,
//        @RequestParam("instanceName") instanceName: String,
//        @RequestBody state: InstanceState
//    ) {
//        val auth = authHeader.decodeBasicAuth()
//        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)
//        val instanceId = dbConnector.getInstances(auth.user).find { it.name == instanceName }?.id
//        if (instanceId == null)
//            throw ResponseStatusException(HttpStatus.NOT_FOUND)
//        dbConnector.setForcedInstanceState(instanceId, state.forcedDeviceState)
//    }

//    @PostMapping("/bff/schedule")
//    fun schedule(
//        @RequestHeader("x-seed") seed: String,
//        @RequestHeader("Authorization") authHeader: String,
//        @RequestParam("instanceName") instanceName: String,
//        @RequestBody weeklySchedule: WeeklySchedule
//    ) {
//        val auth = authHeader.decodeBasicAuth()
//        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)
//        val instanceId = dbConnector.getInstances(auth.user).find { it.name == instanceName }?.id
//        if (instanceId == null)
//            throw ResponseStatusException(HttpStatus.NOT_FOUND)
//        dbConnector.setInstanceSchedule(instanceId, weeklySchedule.toScheduleDto())
//    }

//    private fun ScheduleDto.toWeeklySchedule(): WeeklySchedule = WeeklySchedule(
//        schedules = schedule
//            .mapValues { (_, periods) ->
//                DailySchedule(
//                    periods = periods.periods.map { dto ->
//                        Period(
//                            from = LocalTime.ofSecondOfDay(dto.fromSeconds),
//                            to = LocalTime.ofSecondOfDay(dto.toSeconds),
//                            state = dto.deviceState
//                        )
//                    }
//                )
//            }.ensureAllKeysExist()
//    )
//
//    private fun WeeklySchedule.toScheduleDto() = ScheduleDto(
//        schedule = this.schedules.mapValues { (_, dailySchedule) ->
//            PeriodsDto(periods = dailySchedule.periods.map { period ->
//                PeriodDto(
//                    fromSeconds = period.from.toSecondOfDay().toLong(),
//                    toSeconds = period.to.toSecondOfDay().toLong(),
//                    deviceState = period.state,
//                    deviceStateCountdown = 0
//                )
//            })
//        }
//    )
//
//    private fun Map<Day, DailySchedule>.ensureAllKeysExist(): Map<Day, DailySchedule> =
//        Day.entries.associateWith { this[it] ?: DailySchedule(periods = emptyList()) }

}

private fun DeviceState?.emptyToNull(): DeviceState? = if (this.isNullOrBlank()) null else this

data class InstanceStateResponse(
    val instanceId: InstanceId,
    val instanceName: String,
    val forcedDeviceState: DeviceState?,
    val availableStates: List<DeviceStateDescription>
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
//    val weeklySchedule: WeeklySchedule
)

data class AppUsage(
    val name: String,
    val usageSeconds: Long
)

data class InstanceState(
    val forcedDeviceState: DeviceState?
)
//
//data class WeeklySchedule(
//    val schedules: Map<Day, DailySchedule>
//)
//
//data class DailySchedule(
//    val periods: List<Period>
//)
//
//data class Period(
//    val from: LocalTime,
//    val to: LocalTime,
//    val state: DeviceState
//)
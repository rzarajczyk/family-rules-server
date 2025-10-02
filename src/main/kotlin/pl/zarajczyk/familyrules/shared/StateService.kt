package pl.zarajczyk.familyrules.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service

@Service
class StateService(private val dataRepository: DataRepository) {

    fun getDeviceState(instance: DbInstanceReference): CurrentDeviceState {
        return getDeviceState(dataRepository.getInstance(instance))
    }

    fun getDeviceState(instance: InstanceDto): CurrentDeviceState {
        val automaticState = instance.schedule.getCurrentDeviceState()
        val finalState = instance.forcedDeviceState ?: automaticState

        return CurrentDeviceState(
            forcedState = instance.forcedDeviceState,
            automaticState = automaticState,
            finalState = finalState
        )
    }

    private fun WeeklyScheduleDto.getCurrentDeviceState(): DeviceState {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val day = now.dayOfWeek
        val currentSecondOfDay = now.time.toSecondOfDay()

        val dailySchedule = schedule[day] ?: throw RuntimeException("Schedule is missing day $day")

        val currentPeriod = dailySchedule.periods.find { period ->
            currentSecondOfDay in period.fromSeconds..period.toSeconds - 1
        } ?: throw RuntimeException("Schedule is missing period for $now")

        return currentPeriod.deviceState
    }

}

data class CurrentDeviceState(
    val finalState: DeviceState,
    val automaticState: DeviceState,
    val forcedState: DeviceState?
)
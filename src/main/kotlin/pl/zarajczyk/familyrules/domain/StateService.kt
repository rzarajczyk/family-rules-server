package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.InstanceRef

@Service
class StateService(private val devicesRepository: DevicesRepository) {

    fun getDeviceState(instance: InstanceRef): CurrentDeviceState {
        return getDeviceState(devicesRepository.fetchDeviceDto(instance))
    }

    fun getDeviceState(instance: DeviceDto): CurrentDeviceState {
        val automaticState = instance.schedule.getCurrentDeviceState()
        val finalState = instance.forcedDeviceState ?: automaticState

        return CurrentDeviceState(
            forcedState = instance.forcedDeviceState,
            automaticState = automaticState,
            finalState = finalState
        )
    }

    private fun WeeklyScheduleDto.getCurrentDeviceState(): DeviceStateDto {
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
    val finalState: DeviceStateDto,
    val automaticState: DeviceStateDto,
    val forcedState: DeviceStateDto?
)
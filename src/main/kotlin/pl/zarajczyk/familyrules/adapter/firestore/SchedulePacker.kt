package pl.zarajczyk.familyrules.adapter.firestore

import kotlinx.datetime.DayOfWeek
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.DailyScheduleDto
import pl.zarajczyk.familyrules.domain.PeriodDto
import pl.zarajczyk.familyrules.domain.WeeklyScheduleDto
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto

@Service
class SchedulePacker {

    fun pack(schedule: WeeklyScheduleDto): WeeklyScheduleDto {
        var result = schedule.verify()
        result = result.removeActivePeriods()
        result = result.removeEmptyWeekDays()
        return result
    }

    fun unpack(schedule: WeeklyScheduleDto): WeeklyScheduleDto {
        var result = schedule.verify()
        result = result.addEmptyWeekDays()
        result = result.addActivePeriods()
        return result
    }

    private fun WeeklyScheduleDto.addActivePeriods(): WeeklyScheduleDto {
        val MAX: Long = 60 * 60 * 24
        return WeeklyScheduleDto(
            schedule = schedule.mapValues { (_, daily) ->
                val updatedPeriods = mutableListOf<PeriodDto>()
                if (daily.periods.isEmpty()) {
                    updatedPeriods.add(PeriodDto(0, MAX, DeviceStateDto.Companion.default()))
                } else {
                    var previousToSeconds: Long = 0
                    daily.periods.forEach { period ->
                        if (period.fromSeconds > previousToSeconds) {
                            updatedPeriods.add(
                                PeriodDto(
                                    previousToSeconds,
                                    period.fromSeconds,
                                    DeviceStateDto.Companion.default()
                                )
                            )
                        }
                        updatedPeriods.add(period)
                        previousToSeconds = period.toSeconds
                    }
                    if (daily.periods.last().toSeconds < MAX)
                        updatedPeriods.add(
                            PeriodDto(
                                daily.periods.last().toSeconds,
                                MAX,
                                DeviceStateDto.Companion.default()
                            )
                        )
                }
                DailyScheduleDto(updatedPeriods)
            }
        )
    }

    private fun WeeklyScheduleDto.addEmptyWeekDays(): WeeklyScheduleDto {
        val updatedSchedule = schedule.toMutableMap()
        DayOfWeek.entries.forEach { day ->
            if (!updatedSchedule.containsKey(day)) {
                updatedSchedule[day] = DailyScheduleDto(emptyList())
            }
        }
        return WeeklyScheduleDto(updatedSchedule)
    }

    private fun WeeklyScheduleDto.verify(): WeeklyScheduleDto {
        schedule.forEach { (day, daily) ->
            if (daily.periods.any { it.fromSeconds > it.toSeconds })
                throw RuntimeException("Invalid schedule on $day - from > to in $daily")
            if (daily.periods.zipWithNext().any { (prev, next) -> prev.toSeconds > next.fromSeconds })
                throw RuntimeException("Invalid schedule on $day - prev.to > next.from in $daily")
        }
        return this
    }

    private fun WeeklyScheduleDto.removeEmptyWeekDays(): WeeklyScheduleDto {
        return WeeklyScheduleDto(schedule.filterValues { it.periods.isNotEmpty() })
    }

    private fun WeeklyScheduleDto.removeActivePeriods(): WeeklyScheduleDto {
        return WeeklyScheduleDto(schedule.mapValues { (_, daily) ->
            val filteredPeriods = daily.periods.filter { it.deviceState != DeviceStateDto.Companion.default() }
            DailyScheduleDto(filteredPeriods)
        })
    }

}
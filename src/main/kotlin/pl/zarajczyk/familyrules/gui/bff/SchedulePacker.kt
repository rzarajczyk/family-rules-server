package pl.zarajczyk.familyrules.gui.bff

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.shared.DailyScheduleDto
import pl.zarajczyk.familyrules.shared.Day
import pl.zarajczyk.familyrules.shared.PeriodDto
import pl.zarajczyk.familyrules.shared.WeeklyScheduleDto

@Service
class SchedulePacker {

    fun pack(schedule: WeeklyScheduleDto): WeeklyScheduleDto {
        var result = schedule.verify()
        result = result.removeActivePeriods()
        result = result.removeEmptyWeekDays()
        return schedule
    }

    fun unpack(schedule: WeeklyScheduleDto): WeeklyScheduleDto {
        var result = schedule.verify()
        result = result.addEmptyWeekDays()
        result = result.addActivePeriods()
        return result
    }

    fun WeeklyScheduleDto.addActivePeriods(): WeeklyScheduleDto {
        val MAX: Long = 60 * 60 * 24 - 1
        return WeeklyScheduleDto(
            schedule = schedule.mapValues { (_, daily) ->
                val updatedPeriods = mutableListOf<PeriodDto>()
                if (daily.periods.isEmpty()) {
                    updatedPeriods.add(PeriodDto(0, MAX, "ACTIVE"))
                } else {
                    var previousToSeconds: Long = 0
                    daily.periods.forEach { period ->
                        if (period.fromSeconds > previousToSeconds) {
                            updatedPeriods.add(PeriodDto(previousToSeconds, period.fromSeconds, "ACTIVE"))
                        }
                        updatedPeriods.add(period)
                        previousToSeconds = period.toSeconds
                    }
                    if (daily.periods.last().toSeconds < MAX)
                        updatedPeriods.add(PeriodDto(daily.periods.last().toSeconds, MAX, "ACTIVE"))
                }
                DailyScheduleDto(updatedPeriods)
            }
        )
    }

    fun WeeklyScheduleDto.addEmptyWeekDays(): WeeklyScheduleDto {
        val updatedSchedule = schedule.toMutableMap()
        Day.entries.forEach { day ->
            if (!updatedSchedule.containsKey(day)) {
                updatedSchedule[day] = DailyScheduleDto(emptyList())
            }
        }
        return WeeklyScheduleDto(updatedSchedule)
    }

    fun WeeklyScheduleDto.verify(): WeeklyScheduleDto {
        schedule.forEach { (day, daily) ->
            if (daily.periods.any { it.fromSeconds > it.toSeconds })
                throw RuntimeException("Invalid schedule on $day - from > to in $daily")
            if (daily.periods.zipWithNext().any { (prev, next) -> prev.toSeconds > next.fromSeconds })
                throw RuntimeException("Invalid schedule on $day - prev.to > next.from in $daily")
        }
        return this
    }

    fun WeeklyScheduleDto.removeEmptyWeekDays(): WeeklyScheduleDto {
        return WeeklyScheduleDto(schedule.filterValues { it.periods.isNotEmpty() })
    }

    fun WeeklyScheduleDto.removeActivePeriods(): WeeklyScheduleDto {
        return WeeklyScheduleDto(schedule.mapValues { (_, daily) ->
            val filteredPeriods = daily.periods.filter { it.deviceState != "ACTIVE" }
            DailyScheduleDto(filteredPeriods)
        })
    }

}
package pl.zarajczyk.familyrules.gui.bff

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.shared.DailyScheduleDto
import pl.zarajczyk.familyrules.shared.Day
import pl.zarajczyk.familyrules.shared.PeriodDto
import pl.zarajczyk.familyrules.shared.WeeklyScheduleDto

@Service
class ScheduleUpdater {
    fun addPeriod(schedule: WeeklyScheduleDto, day: Day, newPeriod: PeriodDto): WeeklyScheduleDto {
        val currentSchedule = schedule.schedule[day]?.periods ?: emptyList()

        // We will create a mutable list to hold the updated periods
        val updatedPeriods = mutableListOf<PeriodDto>()

        for (period in currentSchedule) {
            // If the period ends before the new period starts, add it unchanged
            if (period.toSeconds < newPeriod.fromSeconds) {
                updatedPeriods.add(period)
            }
            // If the period starts after the new period ends, add it unchanged
            else if (period.fromSeconds > newPeriod.toSeconds) {
                updatedPeriods.add(period)
            }
            // If the period overlaps with the new period, skip/merge as needed
            else {
                // If the current period starts before the new period, truncate it
                if (period.fromSeconds < newPeriod.fromSeconds) {
                    updatedPeriods.add(period.copy(toSeconds = newPeriod.fromSeconds - 1))
                }
                // If the current period ends after the new period, truncate it
                if (period.toSeconds > newPeriod.toSeconds) {
                    updatedPeriods.add(period.copy(fromSeconds = newPeriod.toSeconds + 1))
                }
            }
        }

        // Add the new period in its correct position
        updatedPeriods.add(newPeriod)

        // Sort the periods by `fromSeconds` to maintain the order
        updatedPeriods.sortBy { it.fromSeconds }

        // Update the schedule for the given day with the new list of periods
        val updatedSchedule = schedule.schedule.toMutableMap()
        updatedSchedule[day] = DailyScheduleDto(updatedPeriods)

        return WeeklyScheduleDto(updatedSchedule)
    }

}
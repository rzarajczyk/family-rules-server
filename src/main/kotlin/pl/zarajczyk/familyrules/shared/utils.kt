package pl.zarajczyk.familyrules.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn


fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())


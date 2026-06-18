package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.Instant
import pl.zarajczyk.familyrules.domain.port.DeviceCommandDto
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

val COMMAND_DELIVERY_TTL: Map<String, Duration> = mapOf(
    "SEND_LOGS" to 6.hours,
    "DISABLE" to 10.minutes,
    "UNINSTALL" to 10.minutes,
)

fun commandDeliveryTtl(commandName: String): Duration? = COMMAND_DELIVERY_TTL[commandName]

fun isCommandUndeliveredDeliveryExpired(command: DeviceCommandDto, now: Instant): Boolean {
    if (command.status != CommandLifecycleStatus.QUEUED) return false
    if (command.lastDeliveredAt != null) return false
    val ttl = commandDeliveryTtl(command.commandName) ?: return false
    val expiresAtEpochSeconds = command.createdAt.epochSeconds + ttl.inWholeSeconds
    return now.epochSeconds >= expiresAtEpochSeconds
}

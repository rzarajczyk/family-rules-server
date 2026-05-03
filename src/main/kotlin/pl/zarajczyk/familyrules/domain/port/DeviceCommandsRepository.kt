package pl.zarajczyk.familyrules.domain.port

import kotlinx.datetime.Instant
import pl.zarajczyk.familyrules.domain.CommandLifecycleStatus
import pl.zarajczyk.familyrules.domain.CommandResultStatus
import pl.zarajczyk.familyrules.domain.DeviceId

interface DeviceCommandsRepository {
    fun create(device: DeviceRef, command: DeviceCommandDto)
    fun get(device: DeviceRef, commandId: String): DeviceCommandDto?
    fun getLatest(device: DeviceRef, commandName: String): DeviceCommandDto?
    fun getPending(device: DeviceRef): List<DeviceCommandDto>
    fun markDelivered(device: DeviceRef, commandIds: Collection<String>, deliveredAt: Instant)
    fun acknowledge(device: DeviceRef, acknowledgements: List<CommandAckDto>)
    fun storeResults(device: DeviceRef, results: List<CommandResultDto>)
    fun hasPending(device: DeviceRef): Boolean
    fun delete(device: DeviceRef, commandId: String)
}

data class DeviceCommandDto(
    val commandId: String,
    val deviceId: DeviceId,
    val commandName: String,
    val status: CommandLifecycleStatus,
    val protocolVersion: Int,
    val createdAt: Instant,
    val lastDeliveredAt: Instant?,
    val acknowledgedAt: Instant?,
    val completedAt: Instant?,
    val deliveryAttempts: Int,
    val resultStatus: CommandResultStatus?,
    val responseType: String?,
    val responsePayloadJson: String?,
)

data class CommandAckDto(
    val commandId: String,
    val receivedAt: Instant,
)

data class CommandResultDto(
    val commandId: String,
    val commandName: String,
    val completedAt: Instant,
    val status: CommandResultStatus,
    val responseType: String,
    val responsePayloadJson: String,
)

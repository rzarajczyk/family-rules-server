package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import pl.zarajczyk.familyrules.domain.port.CommandAckDto
import pl.zarajczyk.familyrules.domain.port.CommandResultDto
import pl.zarajczyk.familyrules.domain.port.DeviceCommandDto
import pl.zarajczyk.familyrules.domain.port.DeviceCommandsRepository
import java.util.UUID

@Service
class DeviceCommandsService(
    private val devicesService: DevicesService,
    private val deviceCommandsRepository: DeviceCommandsRepository,
) {

    fun enqueue(device: Device, commandName: String): DeviceCommandDto {
        if (commandName !in device.getDetails().supportedServerCommands) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Command $commandName is not supported by this device")
        }

        val command = DeviceCommandDto(
            commandId = UUID.randomUUID().toString(),
            deviceId = device.getId(),
            commandName = commandName,
            status = CommandLifecycleStatus.QUEUED,
            protocolVersion = 1,
            createdAt = Clock.System.now(),
            lastDeliveredAt = null,
            acknowledgedAt = null,
            completedAt = null,
            deliveryAttempts = 0,
            resultStatus = null,
            responseType = null,
            responsePayloadJson = null,
        )

        deviceCommandsRepository.create(device.asRef(), command)
        device.update(pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto(
            hasPendingServerCommands = pl.zarajczyk.familyrules.domain.port.ValueUpdate.set(true)
        ))
        return command
    }

    fun getPendingCommands(device: Device): List<DeviceCommandDto> {
        if (!device.getDetails().hasPendingServerCommands) return emptyList()
        return deviceCommandsRepository.getPending(device.asRef())
    }

    fun markDelivered(device: Device, commandIds: Collection<String>) {
        if (commandIds.isEmpty()) return
        deviceCommandsRepository.markDelivered(device.asRef(), commandIds, Clock.System.now())
    }

    fun acknowledge(device: Device, acknowledgements: List<CommandAckDto>) {
        if (acknowledgements.isEmpty()) return
        deviceCommandsRepository.acknowledge(device.asRef(), acknowledgements)
        refreshPendingFlag(device)
    }

    fun submitResults(device: Device, results: List<CommandResultDto>) {
        if (results.isEmpty()) return
        deviceCommandsRepository.storeResults(device.asRef(), results)
        refreshPendingFlag(device)
    }

    fun getForDevice(device: Device, commandId: String): DeviceCommandDto =
        deviceCommandsRepository.get(device.asRef(), commandId) ?: throw CommandNotFoundException(commandId)

    private fun refreshPendingFlag(device: Device) {
        val hasPending = deviceCommandsRepository.hasPending(device.asRef())
        device.update(pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto(
            hasPendingServerCommands = pl.zarajczyk.familyrules.domain.port.ValueUpdate.set(hasPending)
        ))
    }
}

enum class CommandLifecycleStatus {
    QUEUED,
    ACKNOWLEDGED,
    COMPLETED,
    FAILED,
}

enum class CommandResultStatus {
    SUCCEEDED,
    FAILED,
}

class CommandNotFoundException(commandId: String) : RuntimeException("Command $commandId not found")

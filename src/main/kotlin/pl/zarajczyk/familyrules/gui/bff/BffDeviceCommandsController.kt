package pl.zarajczyk.familyrules.gui.bff

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import pl.zarajczyk.familyrules.domain.DeviceCommandsService
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.DevicesService
import pl.zarajczyk.familyrules.domain.UsersService

@RestController
class BffDeviceCommandsController(
    private val devicesService: DevicesService,
    private val deviceCommandsService: DeviceCommandsService,
    private val usersService: UsersService,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping("/bff/instance-commands")
    fun enqueueCommand(
        @RequestParam("instanceId") deviceId: DeviceId,
        @RequestBody request: EnqueueCommandRequest,
        authentication: Authentication,
    ): EnqueueCommandResponse {
        val user = usersService.get(authentication.name)
        val device = devicesService.get(deviceId)
        if (device.getOwner().getDetails().username != user.getDetails().username) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        val command = deviceCommandsService.getOrEnqueue(device, request.commandName)
        return command.toResponse()
    }

    @GetMapping("/bff/instance-commands")
    fun getLatestCommand(
        @RequestParam("instanceId") deviceId: DeviceId,
        @RequestParam("commandName") commandName: String,
        authentication: Authentication,
    ): CommandStatusResponse {
        val user = usersService.get(authentication.name)
        val device = devicesService.get(deviceId)
        if (device.getOwner().getDetails().username != user.getDetails().username) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        val command = deviceCommandsService.getLatestForDevice(device, commandName) ?: return CommandStatusResponse.empty(commandName)
        return command.toStatusResponse()
    }

    @DeleteMapping("/bff/instance-commands")
    fun deleteLatestCommand(
        @RequestParam("instanceId") deviceId: DeviceId,
        @RequestParam("commandName") commandName: String,
        authentication: Authentication,
    ) {
        val user = usersService.get(authentication.name)
        val device = devicesService.get(deviceId)
        if (device.getOwner().getDetails().username != user.getDetails().username) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        val command = deviceCommandsService.getLatestForDevice(device, commandName) ?: return
        deviceCommandsService.delete(device, command.commandId)
    }

    private fun pl.zarajczyk.familyrules.domain.port.DeviceCommandDto.toResponse() = EnqueueCommandResponse(
        commandId = commandId,
        status = toUiStatus(),
        createdAt = createdAt.toString(),
    )

    private fun pl.zarajczyk.familyrules.domain.port.DeviceCommandDto.toStatusResponse() = CommandStatusResponse(
        status = toUiStatus(),
        commandName = commandName,
        createdAt = createdAt.toString(),
        resultStatus = resultStatus?.name,
        responseType = responseType,
        responsePayload = responsePayloadJson?.let { objectMapper.readTree(it) },
    )

    private fun pl.zarajczyk.familyrules.domain.port.DeviceCommandDto.toUiStatus() = when (status) {
        pl.zarajczyk.familyrules.domain.CommandLifecycleStatus.QUEUED,
        pl.zarajczyk.familyrules.domain.CommandLifecycleStatus.ACKNOWLEDGED -> "PENDING"
        pl.zarajczyk.familyrules.domain.CommandLifecycleStatus.COMPLETED,
        pl.zarajczyk.familyrules.domain.CommandLifecycleStatus.FAILED -> "RECEIVED"
    }
}

data class EnqueueCommandRequest(
    val commandName: String,
)

data class EnqueueCommandResponse(
    val commandId: String,
    val status: String,
    val createdAt: String,
)

data class CommandStatusResponse(
    val status: String,
    val commandName: String,
    val createdAt: String,
    val resultStatus: String?,
    val responseType: String?,
    val responsePayload: JsonNode?,
) {
    companion object {
        fun empty(commandName: String) = CommandStatusResponse(
            status = "EMPTY",
            commandName = commandName,
            createdAt = "",
            resultStatus = null,
            responseType = null,
            responsePayload = null,
        )
    }
}

package pl.zarajczyk.familyrules.gui.bff

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
        val command = deviceCommandsService.enqueue(device, request.commandName)
        return EnqueueCommandResponse(command.commandId)
    }

    @GetMapping("/bff/instance-commands/{commandId}")
    fun getCommand(
        @PathVariable commandId: String,
        @RequestParam("instanceId") deviceId: DeviceId,
        authentication: Authentication,
    ): CommandStatusResponse {
        val user = usersService.get(authentication.name)
        val device = devicesService.get(deviceId)
        if (device.getOwner().getDetails().username != user.getDetails().username) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        val command = deviceCommandsService.getForDevice(device, commandId)
        return CommandStatusResponse(
            status = command.status.name,
            commandName = command.commandName,
            resultStatus = command.resultStatus?.name,
            responseType = command.responseType,
            responsePayload = command.responsePayloadJson?.let { objectMapper.readTree(it) },
        )
    }
}

data class EnqueueCommandRequest(
    val commandName: String,
)

data class EnqueueCommandResponse(
    val commandId: String,
)

data class CommandStatusResponse(
    val status: String,
    val commandName: String,
    val resultStatus: String?,
    val responseType: String?,
    val responsePayload: JsonNode?,
)

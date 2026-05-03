package pl.zarajczyk.familyrules.api.v2

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.datetime.Instant
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.DeviceCommandsService
import pl.zarajczyk.familyrules.domain.DevicesService
import pl.zarajczyk.familyrules.domain.get
import pl.zarajczyk.familyrules.domain.port.CommandAckDto
import pl.zarajczyk.familyrules.domain.port.CommandResultDto
import pl.zarajczyk.familyrules.domain.CommandResultStatus

@RestController
class V2DeviceCommandsController(
    private val devicesService: DevicesService,
    private val deviceCommandsService: DeviceCommandsService,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping("/api/v2/command-acks")
    fun acknowledgeCommands(
        @RequestBody request: CommandAcksRequest,
        authentication: Authentication,
    ): StatusResponse {
        val device = devicesService.get(authentication)
        deviceCommandsService.acknowledge(device, request.acks.map {
            CommandAckDto(
                commandId = it.commandId,
                receivedAt = Instant.parse(it.receivedAt),
            )
        })
        return StatusResponse()
    }

    @PostMapping("/api/v2/command-results")
    fun submitCommandResults(
        @RequestBody request: CommandResultsRequest,
        authentication: Authentication,
    ): StatusResponse {
        val device = devicesService.get(authentication)
        deviceCommandsService.submitResults(device, request.results.map {
            CommandResultDto(
                commandId = it.commandId,
                commandName = it.commandName,
                completedAt = Instant.parse(it.completedAt),
                status = CommandResultStatus.valueOf(it.status),
                responseType = it.responseType,
                responsePayloadJson = objectMapper.writeValueAsString(it.responsePayload),
            )
        })
        return StatusResponse()
    }
}

data class CommandAcksRequest(
    val acks: List<CommandAckRequest>,
)

data class CommandAckRequest(
    val commandId: String,
    val receivedAt: String,
)

data class CommandResultsRequest(
    val results: List<CommandResultRequest>,
)

data class CommandResultRequest(
    val commandId: String,
    val commandName: String,
    val completedAt: String,
    val status: String,
    val responseType: String,
    val responsePayload: JsonNode,
)

data class StatusResponse(
    val status: String = "ok",
)

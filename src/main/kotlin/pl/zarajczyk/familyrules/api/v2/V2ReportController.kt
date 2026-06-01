package pl.zarajczyk.familyrules.api.v2

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DeviceCommandDto
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto

@Component
class V2ReportController(
    private val stateService: StateService,
    private val devicesService: DevicesService,
    private val autoAddAppsService: AutoAddAppsService,
    private val deviceCommandsService: DeviceCommandsService,
) {

    @RestController
    inner class ReportRestController {

        @PostMapping(value = ["/api/v2/report"])
        fun report(
            @RequestBody report: ReportRequest,
            authentication: Authentication,
        ): ReportResponse {
            val device = devicesService.get(authentication)
            device.saveScreenTimeReport(
                day = today(),
                screenTimeSeconds = report.screenTimeSeconds,
                applicationsSeconds = report.applicationsSeconds,
                activeApps = report.activeApps,
                mediaPlayingApps = report.mediaPlayingApps,
                isOnline = report.isOnline,
                latitude = report.latitude,
                longitude = report.longitude,
            )
            
            // Update user's lastActivity timestamp (blind write — no prior fetch)
            device.updateOwnerLastActivity(System.currentTimeMillis())

            val pendingCommands = deviceCommandsService.getPendingCommands(device)
            deviceCommandsService.markDelivered(device, pendingCommands.map { it.commandId })

            val response = stateService.calculateCurrentDeviceState(device).finalState.toReportResponse(pendingCommands)
            autoAddAppsService.handleReportedApps(device, report.applicationsSeconds.keys)
            return response
        }
    }

    private fun DeviceStateDto.toReportResponse(commands: List<DeviceCommandDto>) = ReportResponse(
        deviceState = this.deviceState,
        extra = this.extra,
        serverCommands = commands.map { it.toResponse() }
    )

    private fun DeviceCommandDto.toResponse() = ServerCommandResponse(
        commandId = commandId,
        commandName = commandName,
        issuedAt = createdAt.toString(),
        protocolVersion = protocolVersion,
    )

}

data class ReportRequest(
    @JsonProperty("screenTime") val screenTimeSeconds: Long,
    @JsonProperty("applications") val applicationsSeconds: Map<String, Long>,
    @JsonProperty("activeApps") val activeApps: Set<String>? = null,
    @JsonProperty("mediaPlayingApps") val mediaPlayingApps: Set<String>? = null,
    @JsonProperty("isOnline") val isOnline: Boolean = true,
    @JsonProperty("latitude") val latitude: Double? = null,
    @JsonProperty("longitude") val longitude: Double? = null,
)

data class ReportResponse(
    val deviceState: String,
    val extra: String?,
    val serverCommands: List<ServerCommandResponse> = emptyList(),
)

data class ServerCommandResponse(
    val commandId: String,
    val commandName: String,
    val issuedAt: String,
    val protocolVersion: Int,
)

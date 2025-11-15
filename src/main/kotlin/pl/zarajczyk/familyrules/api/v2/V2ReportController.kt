package pl.zarajczyk.familyrules.api.v2

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto
import pl.zarajczyk.familyrules.domain.port.DevicesRepository

@Component
class V2ReportController(private val devicesRepository: DevicesRepository, private val stateService: StateService, private val devicesService: DevicesService) {

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
            )
            val response = stateService.calculateCurrentDeviceState(device).finalState.toReportResponse()
            return response
        }
    }

    private fun DeviceStateDto.toReportResponse() = ReportResponse(
        deviceState = this.deviceState,
        extra = this.extra
    )

}

data class ReportRequest(
    @JsonProperty("screenTime") val screenTimeSeconds: Long,
    @JsonProperty("applications") val applicationsSeconds: Map<String, Long>
)

data class ReportResponse(
    val deviceState: String,
    val extra: String?
)
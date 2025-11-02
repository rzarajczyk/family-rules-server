package pl.zarajczyk.familyrules.api.v2

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.*

@Component
class V2ReportController(private val dataRepository: DataRepository, private val stateService: StateService) {

    @RestController
    inner class ReportRestController {

        @PostMapping(value = ["/api/v2/report"])
        fun report(
            @RequestBody report: ReportRequest,
            authentication: Authentication,
        ): ReportResponse {
            val instanceRef = dataRepository.findAuthenticatedInstance(authentication)
            dataRepository.saveReport(
                instance = instanceRef,
                day = today(),
                screenTimeSeconds = report.screenTimeSeconds,
                applicationsSeconds = report.applicationsSeconds
            )
            val response = stateService.getDeviceState(instanceRef).finalState.toReportResponse()
            return response
        }
    }

    private fun DeviceState.toReportResponse() = ReportResponse(deviceState = this)

}

data class ReportRequest(
    @JsonProperty("screenTime") val screenTimeSeconds: Long,
    @JsonProperty("applications") val applicationsSeconds: Map<String, Long>
)

data class ReportResponse(
    val deviceState: DeviceState
)
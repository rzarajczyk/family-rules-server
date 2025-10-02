package pl.zarajczyk.familyrules.api.v2

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.*

@Component
class V2ReportController(private val dataRepository: DataRepository, private val stateService: StateService) {

    @RestController
    inner class ReportRestController {

        @PostMapping(value = ["/api/v2/report"])
        fun report(
            @RequestBody report: ReportRequest,
            authentication: Authentication,
        ): ReportResponse {
            val instanceId = authentication.principal as InstanceId

//            with(report) {
//                when {
//                    screenTimeSeconds == null && applicationsSeconds == null -> Unit // nothing
//
//                    screenTimeSeconds != null && applicationsSeconds == null ->
//                        throw ValidationError("screenTimeSeconds and applicationsSeconds must be both null or non-null")
//
//                    screenTimeSeconds == null && applicationsSeconds != null ->
//                        throw ValidationError("screenTimeSeconds and applicationsSeconds must be both null or non-null")
//
//                    screenTimeSeconds != null && applicationsSeconds != null ->
//                        dataRepository.saveReport(instanceId, today(), screenTimeSeconds, applicationsSeconds)
//                }
//            }
//
            val instanceRef = dataRepository.getInstanceReference(instanceId) ?: throw RuntimeException("Instance ≪$instanceId≫ not found")
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

class ValidationError(msg: String) : RuntimeException(msg)

data class ReportRequest(
    @JsonProperty("screenTime") val screenTimeSeconds: Long,
    @JsonProperty("applications") val applicationsSeconds: Map<String, Long>
)

data class ReportResponse(
    val deviceState: DeviceState
)
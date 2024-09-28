package pl.zarajczyk.familyrules.api.v1

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.shared.*

@RestController
class ReportController(
    private val dbConnector: DbConnector,
    private val stateService: StateService
) {

    @PostMapping(value = ["/api/report", "/api/v1/report"])
    fun report(
        @RequestBody report: ReportRequest,
        @RequestHeader("Authorization") authHeader: String
    ): ReportResponse = try {
        val auth = authHeader.decodeBasicAuth()
        val instanceId = dbConnector.validateInstanceToken(
            auth.user,
            InstanceId.fromString(report.instanceId),
            auth.pass
        )

        with(report) {
            when {
                screenTimeSeconds == null && applicationsSeconds == null -> Unit // nothing

                screenTimeSeconds != null && applicationsSeconds == null ->
                    throw ValidationError("screenTimeSeconds and applicationsSeconds must be both null or non-null")

                screenTimeSeconds == null && applicationsSeconds != null ->
                    throw ValidationError("screenTimeSeconds and applicationsSeconds must be both null or non-null")

                screenTimeSeconds != null && applicationsSeconds != null ->
                    dbConnector.saveReport(instanceId, today(), screenTimeSeconds, applicationsSeconds)
            }
        }

        stateService.getDeviceState(instanceId).finalState.toReportResponse()
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun DeviceState.toReportResponse() = ReportResponse(deviceState = this)

}

class ValidationError(msg: String) : RuntimeException(msg)

data class ReportRequest(
    @JsonProperty("instanceId") val instanceId: String,
    @JsonProperty("screenTime") val screenTimeSeconds: Long?,
    @JsonProperty("applications") val applicationsSeconds: Map<String, Long>?
)

data class ReportResponse(
    val deviceState: DeviceState,
    val deviceStateCountdown: Long = 0
)
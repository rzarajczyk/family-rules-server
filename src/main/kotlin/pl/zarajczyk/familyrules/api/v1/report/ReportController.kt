package pl.zarajczyk.familyrules.api.v1.report

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.shared.*
import java.util.UUID

@RestController
class ReportController(private val dbConnector: DbConnector) {

    @PostMapping(value = ["/api/report", "/api/v1/report"])
    fun report(
        @RequestBody report: ReportRequest,
        @RequestHeader("Authorization", required = false) authHeader: String
    ): ReportResponse = try {
        val auth = authHeader.decodeBasicAuth()
        val instanceId =  when {
            report.instanceId != null -> dbConnector.validateInstanceToken(auth.user, InstanceId.fromString(report.instanceId), auth.pass)
            report.instanceName != null -> dbConnector.validateInstanceToken(auth.user, report.instanceName, auth.pass)
            else -> throw RuntimeException("Both `instanceName` and `instanceId` are null")
        }
        dbConnector.saveReport(instanceId, today(), report.screenTimeSeconds, report.applicationsSeconds)
        dbConnector.getInstanceState(instanceId)
            ?.toReportResponse()
            ?: ReportResponse.empty()
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun StateDto.toReportResponse() = ReportResponse(
        deviceState = this.deviceState,
        deviceStateCountdown = this.deviceStateCountdown
    )

}

data class ReportRequest(
    @JsonProperty("instanceName") val instanceName: String?,
    @JsonProperty("instanceId") val instanceId: String?,
    @JsonProperty("screenTime") val screenTimeSeconds: Long,
    @JsonProperty("applications") val applicationsSeconds: Map<String, Long>
)

data class ReportResponse(
    val deviceState: DeviceState,
    val deviceStateCountdown: Int
) {
    companion object {
        fun empty() = ReportResponse(DeviceState.ACTIVE, 0)
    }
}
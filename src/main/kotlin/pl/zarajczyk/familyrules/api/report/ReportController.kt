package pl.zarajczyk.familyrules.api.report

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.datetime.toJavaInstant
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.shared.*
import java.time.Instant

@RestController
class ReportController(private val dbConnector: DbConnector, private val securityService: SecurityService) {

    @PostMapping("/api/report")
    fun report(
        @RequestBody report: ReportRequest,
        @RequestHeader("Authorization") authHeader: String?
    ): ReportResponse = try {
        val instanceId = securityService.validateInstanceTokenAndGetInstanceId(authHeader, report.instanceName)
        dbConnector.saveReport(instanceId, today(), report.screenTimeSeconds, report.applicationsSeconds)
        println(report)
        dbConnector.getInstanceState(instanceId)
            ?.toReportResponse()
            ?: ReportResponse.empty()
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun StateDto.toReportResponse() = ReportResponse(
        lockedSince = this.lockedSince?.toJavaInstant(),
        loggedOutSince = this.loggedOutSince?.toJavaInstant()
    )

}

data class ReportRequest(
    @JsonProperty("instanceName") val instanceName: String,
    @JsonProperty("screenTime") val screenTimeSeconds: Long,
    @JsonProperty("applications") val applicationsSeconds: Map<String, Long>
)

data class ReportResponse(
    val lockedSince: Instant?,
    val loggedOutSince: Instant?
) {
    companion object {
        fun empty() = ReportResponse(null, null)
    }
}
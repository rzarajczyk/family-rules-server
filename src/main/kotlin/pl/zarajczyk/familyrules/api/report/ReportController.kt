package pl.zarajczyk.familyrules.api.report

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

@RestController
class ReportController(private val dbConnector: DbConnector) {

    @PostMapping("/report")
    fun report(
        @RequestBody report: ReportRequest,
        @RequestHeader("Authorization", required = false) authHeader: String?
    ): ReportResponse = try {
        val auth = authHeader.decodeBasicAuth()
        val instanceId = dbConnector.validateInstanceToken(auth.user, report.instanceName, auth.pass)
        dbConnector.saveReport(instanceId, today(), report.screenTimeSeconds, report.applicationsSeconds)
        println(report)
        dbConnector.getInstanceState(instanceId)
            ?.toReportResponse()
            ?: ReportResponse.empty()
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun StateDto.toReportResponse() = ReportResponse(
        locked = this.locked
    )

}

data class ReportRequest(
    @JsonProperty("instanceName") val instanceName: String,
    @JsonProperty("screenTime") val screenTimeSeconds: Long,
    @JsonProperty("applications") val applicationsSeconds: Map<String, Long>
)

data class ReportResponse(
    val locked: Boolean
) {
    companion object {
        fun empty() = ReportResponse(false)
    }
}
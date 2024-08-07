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
import pl.zarajczyk.familyrules.shared.DbConnector
import pl.zarajczyk.familyrules.shared.InvalidPassword
import pl.zarajczyk.familyrules.shared.decodeBasicAuth
import pl.zarajczyk.familyrules.shared.today

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
        ReportResponse(NoAction)
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

}

data class ReportRequest(
    @JsonProperty("instanceName") val instanceName: String,
    @JsonProperty("screenTime") val screenTimeSeconds: Long,
    @JsonProperty("applications") val applicationsSeconds: Map<String, Long>
)

enum class ActionType { NO_ACTION, LOCK_SYSTEM }

sealed interface Action {
    val type: ActionType
}

data object NoAction : Action {
    override val type: ActionType = ActionType.NO_ACTION
}

data object LockSystem : Action {
    override val type: ActionType = ActionType.LOCK_SYSTEM
}

data class ReportResponse(
    val action: Action
)
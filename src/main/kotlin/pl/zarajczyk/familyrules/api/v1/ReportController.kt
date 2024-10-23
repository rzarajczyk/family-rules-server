package pl.zarajczyk.familyrules.api.v1

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import pl.zarajczyk.familyrules.shared.*

@Component
class ReportController(private val dbConnector: DbConnector, private val stateService: StateService) {

    @RestController
    inner class ReportRestController(
        private val dbConnector: DbConnector,
        private val stateService: StateService
    ) {

        @PostMapping(value = ["/api/v1/report"])
        fun report(
            @RequestBody report: ReportRequest,
            @RequestHeader("Authorization") authHeader: String
        ): ReportResponse = try {
            handle(authHeader, report)
        } catch (e: InvalidPassword) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
    }

    @Component
    inner class ReportWebSocketHandler(
        private val dbConnector: DbConnector,
        private val stateService: StateService,
        private val objectMapper: ObjectMapper
    ) : TextWebSocketHandler() {

        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            val reportRequest = objectMapper.readValue(message.payload, ReportRequest::class.java)
            val authHeader = session.handshakeHeaders["Authorization"]?.firstOrNull() ?: throw InvalidPassword()

            try {
                val response = handle(authHeader, reportRequest)
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(response)))
            } catch (e: InvalidPassword) {
                session.sendMessage(TextMessage("Invalid password"))
            } catch (e: ValidationError) {
                session.sendMessage(TextMessage(e.message ?: "Validation error"))
            }
        }

        private fun DeviceState.toReportResponse() = ReportResponse(deviceState = this)
    }

    fun handle(authHeader: String, reportRequest: ReportRequest): ReportResponse {
        val auth = authHeader.decodeBasicAuth()
        val instanceId = dbConnector.validateInstanceToken(
            auth.user,
            InstanceId.fromString(reportRequest.instanceId),
            auth.pass
        )

        with(reportRequest) {
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

        val response = stateService.getDeviceState(instanceId).finalState.toReportResponse()
        return response
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
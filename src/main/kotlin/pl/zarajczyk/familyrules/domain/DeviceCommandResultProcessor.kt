package pl.zarajczyk.familyrules.domain

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.CommandResultDto

@Service
class DeviceCommandResultProcessor(
    private val objectMapper: ObjectMapper,
    private val sendLogsResultStorage: SendLogsResultStorage,
) {

    fun process(deviceId: DeviceId, result: CommandResultDto): CommandResultDto {
        if (result.responseType != "SEND_LOGS_V1") {
            return result
        }

        val payload = objectMapper.readTree(result.responsePayloadJson)
        val logsText = payload.path("logsText").asText("")
        val truncated = payload.path("truncated").asText("false") == "true"
        val collectedAt = payload.path("collectedAt").asText(result.completedAt.toString())

        val storedPayload = sendLogsResultStorage.store(
            deviceId = deviceId,
            commandId = result.commandId,
            rawLogsText = logsText,
            truncated = truncated,
            collectedAt = collectedAt,
        )

        return result.copy(responsePayloadJson = objectMapper.writeValueAsString(storedPayload))
    }
}

data class StoredSendLogsPayload(
    val days: List<StoredSendLogsDay> = emptyList(),
    val truncated: Boolean = false,
    val collectedAt: String = "",
)

data class StoredSendLogsDay(
    val day: String,
    val title: String,
    val objectName: String,
)

package pl.zarajczyk.familyrules.adapter.webhook

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.configuration.LogsStorageProperties
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.SendLogsResultStorage
import pl.zarajczyk.familyrules.domain.StoredSendLogsDay
import pl.zarajczyk.familyrules.domain.StoredSendLogsPayload

@Service
class GcsSendLogsResultStorage(
    private val storage: Storage,
    private val properties: LogsStorageProperties,
) : SendLogsResultStorage {

    override fun store(deviceId: DeviceId, commandId: String, rawLogsText: String, truncated: Boolean, collectedAt: String): StoredSendLogsPayload {
        val days = splitByDay(rawLogsText)
            .map { (day, content) ->
                val objectName = "${properties.prefix}/${deviceId}/${commandId}/${day}.txt"
                val blobInfo = BlobInfo.newBuilder(BlobId.of(properties.bucket, objectName))
                    .setContentType("text/plain; charset=utf-8")
                    .build()
                storage.create(blobInfo, content.toByteArray(Charsets.UTF_8))
                StoredSendLogsDay(
                    day = day,
                    title = day,
                    objectName = objectName,
                )
            }

        return StoredSendLogsPayload(
            days = days,
            truncated = truncated,
            collectedAt = collectedAt,
        )
    }

    override fun read(objectName: String): String {
        val blob = storage.get(BlobId.of(properties.bucket, objectName)) ?: return ""
        return blob.getContent().toString(Charsets.UTF_8)
    }

    private fun splitByDay(rawLogsText: String): List<Pair<String, String>> {
        val parts = rawLogsText
            .split(Regex("(?=^===== logs-export-\\d{4}-\\d{2}-\\d{2}\\.txt =====$)", RegexOption.MULTILINE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return parts.mapIndexed { index, part ->
            val match = Regex("^===== logs-export-(\\d{4}-\\d{2}-\\d{2})\\.txt =====$", RegexOption.MULTILINE).find(part)
            val day = match?.groupValues?.get(1) ?: "day-${index + 1}"
            day to part
        }
    }
}

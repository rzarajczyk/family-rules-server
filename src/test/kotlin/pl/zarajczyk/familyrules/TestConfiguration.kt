package pl.zarajczyk.familyrules

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.NoCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.SendLogsResultStorage
import pl.zarajczyk.familyrules.domain.StoredSendLogsPayload
import pl.zarajczyk.familyrules.domain.webhook.WebhookClient
import java.util.concurrent.CopyOnWriteArrayList

@TestConfiguration
@Profile("test")
class TestConfiguration {

    @Value("\${firestore.emulator-host:localhost:8080}")
    private lateinit var emulatorHost: String

    @Bean
    @Primary
    fun testFirestore(): Firestore {
        // Always use Firestore emulator for tests
        return FirestoreOptions.getDefaultInstance()
            .toBuilder()
            .setHost(emulatorHost)  // Firestore emulator host (injected)
            .setProjectId("test-project")
            .setCredentials(NoCredentials.getInstance())
            .build()
            .service
    }

    @Bean
    @Primary
    fun capturingWebhookClient(): CapturingWebhookClient = CapturingWebhookClient()

    @Bean
    @Primary
    fun inMemorySendLogsResultStorage(): InMemorySendLogsResultStorage = InMemorySendLogsResultStorage()
}

/**
 * A WebhookClient that records all payloads sent during tests instead of making real HTTP calls.
 */
class CapturingWebhookClient : WebhookClient {
    val capturedPayloads: MutableList<String> = CopyOnWriteArrayList()
    val capturedUrls: MutableList<String> = CopyOnWriteArrayList()

    override fun sendWebhook(url: String, payload: String) {
        capturedUrls.add(url)
        capturedPayloads.add(payload)
    }

    fun clear() {
        capturedUrls.clear()
        capturedPayloads.clear()
    }
}

class InMemorySendLogsResultStorage : SendLogsResultStorage {
    private val contents = LinkedHashMap<String, String>()

    override fun store(
        deviceId: DeviceId,
        commandId: String,
        rawLogsText: String,
        truncated: Boolean,
        collectedAt: String,
    ): StoredSendLogsPayload {
        val parts = rawLogsText
            .split(Regex("(?=^===== logs-export-\\d{4}-\\d{2}-\\d{2}\\.txt =====$)", RegexOption.MULTILINE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val days = parts.mapIndexed { index, part ->
            val match = Regex("^===== logs-export-(\\d{4}-\\d{2}-\\d{2})\\.txt =====$", RegexOption.MULTILINE).find(part)
            val day = match?.groupValues?.get(1) ?: "day-${index + 1}"
            val objectName = "$deviceId/$commandId/$day.txt"
            contents[objectName] = part
            pl.zarajczyk.familyrules.domain.StoredSendLogsDay(day, day, objectName)
        }

        return StoredSendLogsPayload(days, truncated, collectedAt)
    }

    override fun read(objectName: String): String = contents[objectName] ?: ""
}

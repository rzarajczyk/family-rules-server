package pl.zarajczyk.familyrules.adapter.webhook

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import pl.zarajczyk.familyrules.domain.webhook.WebhookClient

/**
 * Empty implementation of WebhookClient.
 * TODO: Implement actual HTTP POST request logic when ready.
 */
@Component
class EmptyWebhookClient : WebhookClient {
    private val logger = LoggerFactory.getLogger(EmptyWebhookClient::class.java)

    override fun sendWebhook(url: String, payload: String) {
        logger.info("WebhookClient.sendWebhook called (not implemented): url={}, payloadLength={}", url, payload.length)
        // TODO: Implement actual HTTP POST request
    }
}

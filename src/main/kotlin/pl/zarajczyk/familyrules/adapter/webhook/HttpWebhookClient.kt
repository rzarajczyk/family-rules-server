package pl.zarajczyk.familyrules.adapter.webhook

import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import pl.zarajczyk.familyrules.domain.webhook.WebhookClient

/**
 * HTTP-based implementation of WebhookClient using RestTemplate.
 * Sends POST requests with JSON payload to the specified webhook URL.
 */
@Component
class HttpWebhookClient : WebhookClient {
    private val logger = LoggerFactory.getLogger(HttpWebhookClient::class.java)
    private val restTemplate = RestTemplate()

    override fun sendWebhook(url: String, payload: String) {
        try {
            logger.debug("Sending webhook to: {}", url)
            
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            
            val request = HttpEntity(payload, headers)
            val response = restTemplate.postForEntity(url, request, String::class.java)
            
            logger.info("Webhook sent successfully: url={}, status={}", url, response.statusCode)
        } catch (e: Exception) {
            logger.error("Failed to send webhook to: {}", url, e)
            throw e
        }
    }
}

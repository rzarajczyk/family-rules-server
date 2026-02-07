package pl.zarajczyk.familyrules.domain.webhook

/**
 * Client interface for sending webhook notifications.
 */
interface WebhookClient {
    /**
     * Sends a webhook notification to the specified URL with the given payload.
     * 
     * @param url The webhook URL to send the notification to
     * @param payload The JSON payload to send
     * @throws Exception if the webhook notification fails
     */
    fun sendWebhook(url: String, payload: String)
}

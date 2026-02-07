package pl.zarajczyk.familyrules.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "webhook")
class WebhookProperties {
    /**
     * Interval in milliseconds for checking recent activity and scheduling webhook notifications.
     * Default: 30000 (30 seconds)
     */
    var schedulerIntervalMillis: Long = 30000
    
    /**
     * Number of threads in the webhook processor thread pool.
     * Determines how many webhooks can be sent concurrently.
     * Default: 1
     */
    var processorThreads: Int = 1
}

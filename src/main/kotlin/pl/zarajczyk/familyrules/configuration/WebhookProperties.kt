package pl.zarajczyk.familyrules.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "webhook")
class WebhookProperties {
    /**
     * Interval in milliseconds for checking recent activity and scheduling webhook notifications.
     * Default: 30000 (30 seconds)
     */
    var schedulerIntervalMillis: Long = 30000
}

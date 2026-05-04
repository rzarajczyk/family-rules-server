package pl.zarajczyk.familyrules.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "logs-storage")
class LogsStorageProperties {
    var bucket: String = ""
    var prefix: String = "device-command-logs"
}

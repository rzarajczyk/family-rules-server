package pl.zarajczyk.familyrules.shared

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("configuration")
data class AppConfiguration(
    val security: SecurityAppConfiguration
)

data class SecurityAppConfiguration(
    val enable: Boolean = true
)
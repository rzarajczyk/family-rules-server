package pl.zarajczyk.familyrules.shared

import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.properties.Delegates

@ConfigurationProperties(prefix = "database-initialization")
class DatabaseInitializationProperties {
    var enabled by Delegates.notNull<Boolean>()
    lateinit var username: String
    lateinit var password: String
}

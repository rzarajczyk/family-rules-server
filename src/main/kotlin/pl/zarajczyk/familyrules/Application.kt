package pl.zarajczyk.familyrules

import org.jetbrains.exposed.spring.autoconfigure.ExposedAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import pl.zarajczyk.familyrules.shared.AppConfiguration

@SpringBootApplication
@ImportAutoConfiguration(
    value = [ExposedAutoConfiguration::class],
//    exclude = [DataSourceTransactionManagerAutoConfiguration::class]
)
@EnableConfigurationProperties(AppConfiguration::class)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

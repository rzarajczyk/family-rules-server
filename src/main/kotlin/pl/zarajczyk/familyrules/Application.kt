package pl.zarajczyk.familyrules

import org.jetbrains.exposed.spring.autoconfigure.ExposedAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@ImportAutoConfiguration(
    value = [ExposedAutoConfiguration::class/*, SecurityConfig::class*/],
//    exclude = [DataSourceTransactionManagerAutoConfiguration::class]
)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

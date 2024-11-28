package pl.zarajczyk.familyrules

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import pl.zarajczyk.familyrules.shared.DatabaseInitializationProperties

@Testcontainers
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@DirtiesContext
abstract class AbstractIntegrationTest {

    companion object {
        @Container
        private val postgresContainer = PostgreSQLContainer<Nothing>("postgres:17").apply {
            withDatabaseName("test")
            withUsername("test")
            withPassword("test")
        }


        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)
        }
    }

    @Autowired
    lateinit var databaseInitializationProperties: DatabaseInitializationProperties

    fun user() = databaseInitializationProperties.username

    fun password() = databaseInitializationProperties.password
}
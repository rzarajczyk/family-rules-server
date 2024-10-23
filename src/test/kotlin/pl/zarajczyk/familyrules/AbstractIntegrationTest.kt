package pl.zarajczyk.familyrules

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.containers.PostgreSQLContainer
import pl.zarajczyk.familyrules.shared.DatabaseInitializationProperties

@ExtendWith(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    companion object {
        private val postgresContainer = PostgreSQLContainer<Nothing>("postgres:17").apply {
            withDatabaseName("test")
            withUsername("test")
            withPassword("test")
        }

        @BeforeAll
        @JvmStatic
        fun startContainer() {
            postgresContainer.start()
            System.setProperty("DB_URL", postgresContainer.jdbcUrl)
            System.setProperty("DB_USERNAME", postgresContainer.username)
            System.setProperty("DB_PASSWORD", postgresContainer.password)
        }

        @AfterAll
        @JvmStatic
        fun stopContainer() {
            postgresContainer.stop()
        }
    }

    @Autowired
    lateinit var databaseInitializationProperties: DatabaseInitializationProperties

    fun user() = databaseInitializationProperties.username

    fun password() = databaseInitializationProperties.password
}
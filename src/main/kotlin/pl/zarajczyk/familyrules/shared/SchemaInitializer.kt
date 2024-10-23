package pl.zarajczyk.familyrules.shared

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.properties.Delegates

@Component
@Transactional
@EnableConfigurationProperties(DatabaseInitializationProperties::class)
class SchemaInitializer(private val databaseInitializationProperties: DatabaseInitializationProperties) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        SchemaUtils.create(
            DbConnector.Users,
            DbConnector.Instances,
            DbConnector.DeviceStates,
            DbConnector.ScreenTimes
        )

        if (databaseInitializationProperties.enabled) {
            if (DbConnector.Users.selectAll().count() == 0L) {
                DbConnector.Users.insert {
                    it[DbConnector.Users.username] = databaseInitializationProperties.username
                    it[DbConnector.Users.passwordSha256] = databaseInitializationProperties.password.sha256()
                }
            }
        }
    }
}

@ConfigurationProperties(prefix = "database-initialization")
class DatabaseInitializationProperties {
    var enabled by Delegates.notNull<Boolean>()
    lateinit var username: String
    lateinit var password: String
}
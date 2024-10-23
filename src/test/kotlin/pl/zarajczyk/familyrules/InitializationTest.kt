package pl.zarajczyk.familyrules

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import pl.zarajczyk.familyrules.shared.DbConnector

class InitializationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var dbConnector: DbConnector

    @Test
    fun `context should be up`() {
        // nothing - spring should just start
    }

    @Test
    fun `should have users table`() {
        // when
        dbConnector.validatePassword(user(), password())

        // then
        // no exception should be thrown
    }

}
package pl.zarajczyk.familyrules

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import pl.zarajczyk.familyrules.shared.DataRepository

class InitializationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var dataRepository: DataRepository

    @Test
    fun `context should be up`() {
        // nothing - spring should just start
    }

    @Test
    fun `should have users table`() {
        // when
        dataRepository.validatePassword(user(), password())

        // then
        // no exception should be thrown
    }

}
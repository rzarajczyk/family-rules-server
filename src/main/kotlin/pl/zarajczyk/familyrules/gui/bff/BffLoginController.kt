package pl.zarajczyk.familyrules.gui.bff

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.DbConnector
import pl.zarajczyk.familyrules.shared.InvalidPassword
import pl.zarajczyk.familyrules.shared.decodeBasicAuth
import pl.zarajczyk.familyrules.shared.randomSeed

@RestController
class BffLoginController(private val dbConnector: DbConnector) {

    @PostMapping("/bff/login")
    fun login(@RequestHeader("Authorization") authHeader: String): LoginResponse =
        try {
            val auth = authHeader.decodeBasicAuth()
            val seed = randomSeed()
            val token = dbConnector.validatePasswordAndCreateOneTimeToken(auth.user, auth.pass, seed)
            LoginResponse(true, seed, token)
        } catch (e: InvalidPassword) {
            LoginResponse(false)
        }
}


data class LoginResponse(
    val success: Boolean,
    val seed: String? = null,
    val token: String? = null
)

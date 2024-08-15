package pl.zarajczyk.familyrules.shared

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.util.*

@Service
class SecurityService(
    private val dbConnector: DbConnector,
    private val appConfiguration: AppConfiguration
) {

    fun validatePassword(authHeader: String?): UserName {
        val auth = authHeader.decodeBasicAuth()
        validateUser(auth)
        return auth.user
    }

    fun createOneTimeToken(authHeader: String?): OneTimeToken {
        val auth = authHeader.decodeBasicAuth()
        val hash = validateUser(auth)
        val seed = randomSeed()
        val token = createOneTimeToken(hash, seed)
        return OneTimeToken(seed, token)
    }

    fun validateOneTimeToken(authHeader: String?, seed: String): UserName {
        val auth = authHeader.decodeBasicAuth()
        validateUser(auth) { hash -> createOneTimeToken(hash, seed) }
        return auth.user
    }

    private fun validateUser(auth: BasicAuth, hasher: (String) -> String = { it }): String {
        val hash = dbConnector.getPasswordHash(auth.user) ?: throw InvalidPassword()
        if (appConfiguration.security.enable && hasher(hash) != auth.pass.hash())
            throw InvalidPassword()
        return hash
    }

    fun createInstanceToken() = randomSeed().let { InstanceToken(it, it.hash()) }

    fun validateInstanceTokenAndGetInstanceId(authHeader: String?, instanceName: String): InstanceId {
        val auth = authHeader.decodeBasicAuth()
        val instance = dbConnector.getInstance(auth.user, instanceName) ?: throw InvalidPassword()
        if (appConfiguration.security.enable && instance.tokenHash != auth.pass.hash())
            throw InvalidPassword()
        return instance.id
    }

    private fun String?.decodeBasicAuth(): BasicAuth {
        if (this == null || this.lowercase().startsWith("Basic"))
            throw InvalidAuthHeader()
        try {
            return this
                .substring("Basic".length)
                .trim()
                .let { Base64.getDecoder().decode(it).decodeToString() }
                .split(":", ignoreCase = false, limit = 2)
                .let { BasicAuth(it[0], it[1]) }
        } catch (e: Exception) {
            throw InvalidAuthHeader()
        }
    }

    private fun createOneTimeToken(passwordHash: String, seed: String) = "${seed}/${passwordHash}".hash()

    private fun String.hash() = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray()).joinToString("") { "%02x".format(it) }

    private fun randomSeed() = UUID.randomUUID().toString()
}

data class OneTimeToken(
    val seed: String,
    val token: String
)

data class InstanceToken(
    val plain: String,
    val hash: String
)

class InvalidPassword : RuntimeException("Invalid password")

class InvalidAuthHeader : ResponseStatusException(HttpStatus.FORBIDDEN, "Unable to authorize request")

data class BasicAuth(
    val user: String,
    val pass: String
)
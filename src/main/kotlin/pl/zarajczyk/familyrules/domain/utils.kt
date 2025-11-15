package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.security.MessageDigest
import java.util.*

import org.springframework.security.core.Authentication
import pl.zarajczyk.familyrules.domain.port.DevicesRepository

fun DevicesService.get(authentication: Authentication): Device {
    return get(authentication.principal as DeviceId)
}

fun String?.decodeBasicAuth(): BasicAuth {
    if (this == null || this.lowercase().startsWith("Basic"))
        throw RuntimeException("Missing Basic Auth header")
    try {
        return this
            .substring("Basic".length)
            .trim()
            .let { Base64.getDecoder().decode(it).decodeToString() }
            .split(":", ignoreCase = false, limit = 2)
            .let { BasicAuth(it[0], it[1]) }
    } catch (e: Exception) {
        throw RuntimeException("Invalid Basic Auth header", e)
    }
}

fun String.sha256() = MessageDigest
    .getInstance("SHA-256")
    .digest(toByteArray()).joinToString("") { "%02x".format(it) }

fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

data class BasicAuth(
    val user: String,
    val pass: String
)
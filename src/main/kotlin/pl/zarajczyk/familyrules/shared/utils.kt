package pl.zarajczyk.familyrules.shared

import java.security.MessageDigest
import java.util.*

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


data class BasicAuth(
    val user: String,
    val pass: String
)

fun main() {
    println("admin".sha256())
}
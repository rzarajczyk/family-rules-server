package pl.zarajczyk.familyrules.configuration.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.GenericFilterBean
import pl.zarajczyk.familyrules.domain.DeviceNotFoundException
import pl.zarajczyk.familyrules.domain.DevicesService
import pl.zarajczyk.familyrules.domain.InstanceId
import pl.zarajczyk.familyrules.domain.decodeBasicAuth
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class ApiV2KeyAuthFilter(
    private val devicesService: DevicesService,
    private val excludedUris: Set<String>
) : GenericFilterBean() {

    // Cache configuration
    companion object {
        const val cacheExpirationMinutes = 30L
        const val maxCacheSize = 1024
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    // Cache storage: key is "instanceId:tokenHash", value is cache entry
    private val authCache = ConcurrentHashMap<String, AuthCacheEntry>()

    private data class AuthCacheEntry(
        val instanceId: InstanceId,
        val timestamp: Instant
    ) {
        fun isExpired(): Boolean {
            return timestamp.plusSeconds(cacheExpirationMinutes * 60).isBefore(Instant.now())
        }
    }

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain?
    ) {
        try {
            request as HttpServletRequest
            if (excludedUris.contains(request.requestURI)) {
                chain?.doFilter(request, response)
                return
            }
            val authentication = authorize(request as HttpServletRequest)
            SecurityContextHolder.getContext().authentication = authentication
            chain?.doFilter(request, response)
        } catch (e: BadCredentialsException) {
            val httpResponse = response as HttpServletResponse
            httpResponse.status = HttpServletResponse.SC_UNAUTHORIZED
            httpResponse.contentType = MediaType.APPLICATION_JSON_VALUE
            httpResponse.writer.apply {
                print(e.message)
                flush()
                close()
            }
        }
    }

    private fun authorize(request: HttpServletRequest): ApiKeyAuthenticationToken {
        val authHeader = request.getHeader("Authorization") ?: throw MissingHeaderException()
        val auth = authHeader.decodeBasicAuth()

        val instanceId = InstanceId.fromString(auth.user)

        // Check cache first
        val cacheKey = "${instanceId}:${auth.pass.hashCode()}"
        val cachedEntry = authCache[cacheKey]

        if (cachedEntry != null && !cachedEntry.isExpired()) {
            logger.info("Instance ≪${cachedEntry.instanceId}≫ validated successfully using the cache")
            return ApiKeyAuthenticationToken.authorized(cachedEntry.instanceId, auth.pass)
        }

        cleanupExpiredEntries()
        val isValid = try {
            devicesService.get(instanceId).validateToken(auth.pass)
        } catch (_: DeviceNotFoundException) {
            false
        }
        return if (isValid) {
            logger.info("Instance ≪${instanceId}≫ validated successfully using the database")
            cacheValidationResult(cacheKey, instanceId)
            ApiKeyAuthenticationToken.authorized(instanceId, auth.pass)
        } else {
            logger.info("Instance ≪${instanceId}≫ NOT validated successfully")
            throw UnauthorizedException()
        }

    }

    private fun cacheValidationResult(cacheKey: String, instanceId: InstanceId) {
        // Ensure cache doesn't exceed max size
        if (authCache.size >= maxCacheSize) {
            // Remove oldest entries (simple LRU approximation)
            val oldestKeys = authCache.entries
                .sortedBy { it.value.timestamp }
                .take(maxCacheSize / 4) // Remove 25% of entries
                .map { it.key }

            oldestKeys.forEach { authCache.remove(it) }
        }

        authCache[cacheKey] = AuthCacheEntry(instanceId, Instant.now())
    }

    private fun cleanupExpiredEntries() {
        val expiredKeys = authCache.entries
            .filter { it.value.isExpired() }
            .map { it.key }

        expiredKeys.forEach { authCache.remove(it) }
    }
}


class ApiKeyAuthenticationToken private constructor(
    private val instanceId: InstanceId,
    private val token: String,
    authorized: Boolean
) :
    AbstractAuthenticationToken(emptyList<GrantedAuthority>()) {
    init {
        isAuthenticated = authorized
    }

    override fun getPrincipal() = instanceId
    override fun getCredentials() = token

    companion object {
        fun authorized(instanceId: InstanceId, token: String) = ApiKeyAuthenticationToken(instanceId, token, true)
    }
}

class MissingHeaderException : BadCredentialsException("Missing header")
class UnauthorizedException : BadCredentialsException("Unauthorized")
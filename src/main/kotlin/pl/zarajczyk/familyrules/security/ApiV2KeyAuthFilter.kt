package pl.zarajczyk.familyrules.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.GenericFilterBean
import pl.zarajczyk.familyrules.shared.DbConnector
import pl.zarajczyk.familyrules.shared.InstanceId
import pl.zarajczyk.familyrules.shared.decodeBasicAuth

class ApiV2KeyAuthFilter(
    private val dbConnector: DbConnector,
    private val excludedUris: Set<String>
) : GenericFilterBean() {
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
        dbConnector.validateInstanceToken(instanceId, auth.pass) ?: throw UnauthorizedException()

        val authentication = ApiKeyAuthenticationToken.authorized(instanceId, auth.pass)
        return authentication
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
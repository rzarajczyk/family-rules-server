package pl.zarajczyk.familyrules.configuration.security

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
import pl.zarajczyk.familyrules.domain.UsersService

class IntegrationApiKeyAuthFilter(
    private val usersService: UsersService,
) : GenericFilterBean() {

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain?
    ) {
        try {
            val authentication = authorize(request as HttpServletRequest)
            SecurityContextHolder.getContext().authentication = authentication
            chain?.doFilter(request, response)
        } catch (e: BadCredentialsException) {
            val httpResponse = response as HttpServletResponse
            httpResponse.status = HttpServletResponse.SC_UNAUTHORIZED
            httpResponse.contentType = MediaType.APPLICATION_JSON_VALUE
            httpResponse.writer.apply {
                print("""{"error":"${e.message}"}""")
                flush()
                close()
            }
        }
    }

    private fun authorize(request: HttpServletRequest): IntegrationApiAuthenticationToken {
        val authHeader = request.getHeader("Authorization") ?: throw MissingHeaderException()
        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) throw MissingHeaderException()
        val token = authHeader.substring("Bearer ".length).trim()

        val user = usersService.getByIntegrationApiToken(token) ?: throw UnauthorizedException()
        val username = user.fetchDetails().username
        return IntegrationApiAuthenticationToken(username, token)
    }
}

class IntegrationApiAuthenticationToken(
    private val username: String,
    private val token: String,
) : AbstractAuthenticationToken(emptyList<GrantedAuthority>()) {
    init {
        isAuthenticated = true
    }

    override fun getPrincipal() = username
    override fun getCredentials() = token
    override fun getName() = username
}

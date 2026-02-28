package pl.zarajczyk.familyrules.configuration.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Logs every HTTP request/response for the API filter chains it is registered in.
 * Output example: `POST /api/v2/report -> 200 (45 ms)`
 */
class ApiRequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ApiRequestLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val start = System.currentTimeMillis()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - start
            log.info(
                "{} {} -> {} ({} ms)",
                request.method,
                request.requestURI,
                response.status,
                duration,
            )
        }
    }
}

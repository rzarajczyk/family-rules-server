package pl.zarajczyk.familyrules.shared

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import pl.zarajczyk.familyrules.api.v1.ReportController.ReportWebSocketHandler

@Configuration
@EnableWebSocket
class WebSocketConfig(private val reportWebSocketHandler: ReportWebSocketHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(reportWebSocketHandler, "/api/v1/streaming-report").setAllowedOrigins("*")
    }
}
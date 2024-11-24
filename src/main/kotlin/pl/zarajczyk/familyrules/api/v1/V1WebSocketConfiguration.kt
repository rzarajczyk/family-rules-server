package pl.zarajczyk.familyrules.api.v1

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import pl.zarajczyk.familyrules.api.v1.V1ReportController.ReportWebSocketHandler

@Configuration
@EnableWebSocket
class V1WebSocketConfig(private val reportWebSocketHandler: ReportWebSocketHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(reportWebSocketHandler, "/api/v1/streaming-report").setAllowedOrigins("*")
    }
}
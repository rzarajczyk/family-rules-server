package pl.zarajczyk.familyrules.api

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        ex: HttpMessageNotReadableException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("JSON parsing error for request: ${request.getDescription(false)}", ex)
        
        val errorMessage = when (ex.cause) {
            is JsonParseException -> "Invalid JSON format: ${ex.cause?.message}"
            is JsonMappingException -> "JSON mapping error: ${ex.cause?.message}"
            else -> "Invalid request body format"
        }
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ErrorResponse(
                error = "BAD_REQUEST",
                message = errorMessage,
                timestamp = System.currentTimeMillis()
            ))
    }

    @ExceptionHandler(JsonParseException::class)
    fun handleJsonParseException(
        ex: JsonParseException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("JSON parse error for request: ${request.getDescription(false)}", ex)
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ErrorResponse(
                error = "BAD_REQUEST",
                message = "Invalid JSON format: ${ex.message}",
                timestamp = System.currentTimeMillis()
            ))
    }

    @ExceptionHandler(JsonMappingException::class)
    fun handleJsonMappingException(
        ex: JsonMappingException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("JSON mapping error for request: ${request.getDescription(false)}", ex)
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ErrorResponse(
                error = "BAD_REQUEST",
                message = "JSON mapping error: ${ex.message}",
                timestamp = System.currentTimeMillis()
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error for request: ${request.getDescription(false)}", ex)
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ErrorResponse(
                error = "INTERNAL_SERVER_ERROR",
                message = "An unexpected error occurred",
                timestamp = System.currentTimeMillis()
            ))
    }
}

data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Long
)



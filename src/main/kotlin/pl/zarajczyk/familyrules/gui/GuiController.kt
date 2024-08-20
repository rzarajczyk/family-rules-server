package pl.zarajczyk.familyrules.gui

import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.function.ServerRequest.Headers

@RestController
class GuiController {

    @GetMapping("/")
    fun root(response: HttpServletResponse) = response.sendRedirect("/gui/login.html")

    @GetMapping("/gui/{file}")
    fun get(
        @PathVariable("file") file: String
    ): ResponseEntity<Resource> {
        val resource = javaClass
            .classLoader
            .getResource("gui/$file")
            .readBytes()
            .let { ByteArrayResource(it) }
        val headers = HttpHeaders()
        headers[HttpHeaders.CONTENT_TYPE] = when (file.extension()) {
            "htm", "html" -> "text/html"
            "js" -> "text/javascript"
            "css" -> "text/css"
            "png" -> "image/png"
            "mustache" -> "text/html"
            "handlebars" -> "text/html"
            else -> throw RuntimeException("Unknown MIME_TYPE for ${file.extension()}")
        }
        return ResponseEntity(resource, headers, HttpStatus.OK)
    }

    private fun String.extension() = this.substring(this.lastIndexOf(".")+1)

}
package pl.zarajczyk.familyrules.gui

import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class GuiController {

    @GetMapping("/")
    fun root(response: HttpServletResponse) = response.sendRedirect("/gui/index.html")

    @GetMapping("/gui/{file}")
    fun get(
        @PathVariable("file") file: String
    ): Resource = javaClass
        .classLoader
        .getResource("gui/$file")
        .readBytes()
        .let { ByteArrayResource(it) }

}
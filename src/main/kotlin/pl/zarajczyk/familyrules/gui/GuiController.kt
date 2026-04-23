package pl.zarajczyk.familyrules.gui

import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class GuiController {

    @GetMapping("/")
    fun root(response: HttpServletResponse) = response.sendRedirect("/gui/index.html")
}

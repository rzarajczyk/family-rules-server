package pl.zarajczyk.familyrules.gui.bff

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RestController
class BffTimeController {
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    @GetMapping("/bff/time")
    fun time() = CurrentTime(
        time = formatter.format(Instant.now()),
        tz = ZoneId.systemDefault().id
    )

}

data class CurrentTime(
    val time: String,
    val tz: String
)
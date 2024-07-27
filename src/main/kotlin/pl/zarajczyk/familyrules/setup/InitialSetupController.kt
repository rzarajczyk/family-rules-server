package pl.zarajczyk.familyrules.setup

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class InitialSetupController {

    @PostMapping("/setup")
    fun setup(
        @RequestBody data: InitialSetupRequest,
        @RequestHeader("Authorization", required = false) authHeader: String?
    ): InitialSetupResponse {
        val auth = authHeader.decodeBasicAuth()
        if (auth.user == "admin" && auth.pass == "admin") {
            if (data.instanceName.length < 3)
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "InstanceName is too short")
            return InitialSetupResponse(InitialSetupStatus.SUCCESS, "abc-123-${data.instanceName}")
        }
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

}

data class InitialSetupRequest(
    val instanceName: String
)

enum class InitialSetupStatus {
    SUCCESS
}

data class InitialSetupResponse(
    val status: InitialSetupStatus,
    val token: String?
)
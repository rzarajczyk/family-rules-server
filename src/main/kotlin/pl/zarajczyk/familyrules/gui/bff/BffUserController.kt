package pl.zarajczyk.familyrules.gui.bff

import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.shared.*

@RestController
class BffUserController(
    private val dataRepository: DataRepository
) {

    @GetMapping("/bff/users")
    fun getAllUsers(authentication: Authentication): GetUsersResponse {
        // Check if user has admin access
        val currentUser = dataRepository.findUser(authentication.name)
        if (currentUser?.accessLevel != AccessLevel.ADMIN) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required")
        }
        
        val users = dataRepository.getAllUsers()
        return GetUsersResponse(users)
    }

    @GetMapping("/bff/current-user")
    fun getCurrentUser(authentication: Authentication): CurrentUserResponse {
        val user = dataRepository.findUser(authentication.name)
        return CurrentUserResponse(
            username = authentication.name,
            accessLevel = user?.accessLevel ?: AccessLevel.ADMIN
        )
    }
}

data class GetUsersResponse(
    val users: List<UserDto>
)

data class CurrentUserResponse(
    val username: String,
    val accessLevel: AccessLevel
)
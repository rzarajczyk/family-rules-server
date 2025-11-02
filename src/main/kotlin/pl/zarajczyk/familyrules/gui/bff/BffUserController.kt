package pl.zarajczyk.familyrules.gui.bff

import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.domain.*

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

    @DeleteMapping("/bff/users/{username}")
    fun deleteUser(
        @PathVariable username: String,
        authentication: Authentication
    ): DeleteUserResponse {
        // Check if user has admin access
        val currentUser = dataRepository.findUser(authentication.name)
        if (currentUser?.accessLevel != AccessLevel.ADMIN) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required")
        }
        
        // Prevent deleting self
        if (username == authentication.name) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete your own account")
        }
        
        dataRepository.deleteUser(username)
        return DeleteUserResponse(success = true, message = "User deleted successfully")
    }

    @PostMapping("/bff/users/{username}/reset-password")
    fun resetPassword(
        @PathVariable username: String,
        @RequestBody request: ResetPasswordRequest,
        authentication: Authentication
    ): ResetPasswordResponse {
        // Check if user has admin access
        val currentUser = dataRepository.findUser(authentication.name)
        if (currentUser?.accessLevel != AccessLevel.ADMIN) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required")
        }
        
        dataRepository.changePassword(username, request.newPassword)
        return ResetPasswordResponse(success = true, message = "Password reset successfully")
    }

    @PostMapping("/bff/users")
    fun createUser(
        @RequestBody request: CreateUserRequest,
        authentication: Authentication
    ): CreateUserResponse {
        // Check if user has admin access
        val currentUser = dataRepository.findUser(authentication.name)
        if (currentUser?.accessLevel != AccessLevel.ADMIN) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required")
        }
        
        // Check if user already exists
        if (dataRepository.findUser(request.username) != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "User already exists")
        }
        
        dataRepository.createUser(request.username, request.password, request.accessLevel)
        return CreateUserResponse(success = true, message = "User created successfully")
    }
}

data class GetUsersResponse(
    val users: List<UserDto>
)

data class CurrentUserResponse(
    val username: String,
    val accessLevel: AccessLevel
)

data class DeleteUserResponse(
    val success: Boolean,
    val message: String
)

data class ResetPasswordRequest(
    val newPassword: String
)

data class ResetPasswordResponse(
    val success: Boolean,
    val message: String
)

data class CreateUserRequest(
    val username: String,
    val password: String,
    val accessLevel: AccessLevel
)

data class CreateUserResponse(
    val success: Boolean,
    val message: String
)
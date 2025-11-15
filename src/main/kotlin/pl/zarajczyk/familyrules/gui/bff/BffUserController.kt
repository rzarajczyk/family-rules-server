package pl.zarajczyk.familyrules.gui.bff

import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.domain.AccessLevel
import pl.zarajczyk.familyrules.domain.User
import pl.zarajczyk.familyrules.domain.UsersService

@RestController
class BffUserController(
    private val usersService: UsersService
) {

    @GetMapping("/bff/users")
    fun getAllUsers(authentication: Authentication): GetUsersResponse =
        usersService.withUserContext(authentication.name) { user ->
            user.mustBeAdmin()

            GetUsersResponse(
                users = usersService.getAllUsers().map {
                    GetUsersUserResponse(it.fetchDetails().username)
                }
            )
        }

    @GetMapping("/bff/current-user")
    fun getCurrentUser(authentication: Authentication): CurrentUserResponse =
        usersService.withUserContext(authentication.name) { user ->
            val details = user.fetchDetails()
            CurrentUserResponse(
                username = details.username,
                accessLevel = details.accessLevel
            )
        }

    @DeleteMapping("/bff/users/{username}")
    fun deleteUser(
        @PathVariable username: String,
        authentication: Authentication
    ): DeleteUserResponse =
        usersService.withUserContext(authentication.name) { user ->
            user.mustBeAdmin()

            if (user.fetchDetails().username == username) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete your own account")
            }

            usersService.withUserContext(username) { userToBeDeleted ->
                userToBeDeleted.delete()
            }

            DeleteUserResponse(success = true, message = "User deleted successfully")
        }


    @PostMapping("/bff/users/{username}/reset-password")
    fun resetPassword(
        @PathVariable username: String,
        @RequestBody request: ResetPasswordRequest,
        authentication: Authentication
    ): ResetPasswordResponse =
        usersService.withUserContext(authentication.name) { user ->
            user.mustBeAdmin()

            usersService.withUserContext(username) { userToBeUpdated ->
                userToBeUpdated.changePassword(request.newPassword)
            }

            ResetPasswordResponse(success = true, message = "Password reset successfully")
        }

    @PostMapping("/bff/users")
    fun createUser(
        @RequestBody request: CreateUserRequest,
        authentication: Authentication
    ): CreateUserResponse =
        usersService.withUserContext(authentication.name) { user ->
            user.mustBeAdmin()

            if (usersService.userExists(request.username)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "User already exists")
            }

            usersService.createUser(request.username, request.password, request.accessLevel)

            CreateUserResponse(success = true, message = "User created successfully")
        }

    private fun User.mustBeAdmin() {
        if (fetchDetails().accessLevel != AccessLevel.ADMIN) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required")
        }
    }
}

data class GetUsersResponse(
    val users: List<GetUsersUserResponse>
)

data class GetUsersUserResponse(
    val username: String
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
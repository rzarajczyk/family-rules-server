package pl.zarajczyk.familyrules.gui.bff

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.InvalidPassword
import pl.zarajczyk.familyrules.domain.UsersService

@RestController
class BffSettingsController(
    private val usersService: UsersService
) {

    @PostMapping("/bff/change-password")
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
        authentication: Authentication
    ): ChangePasswordResponse {
        return try {
            val user = usersService.get(authentication.name)

            user.validatePassword(request.currentPassword)
            user.changePassword(request.newPassword)

            ChangePasswordResponse(success = true, message = "Password changed successfully")
        } catch (_: InvalidPassword) {
            ChangePasswordResponse(success = false, message = "Current password is incorrect")
        } catch (_: Exception) {
            ChangePasswordResponse(success = false, message = "Failed to change password")
        }
    }
}

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class ChangePasswordResponse(
    val success: Boolean,
    val message: String
)

package pl.zarajczyk.familyrules.gui.bff

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import pl.zarajczyk.familyrules.domain.DataRepository
import pl.zarajczyk.familyrules.domain.InvalidPassword

@RestController
class BffSettingsController(
    private val dataRepository: DataRepository
) {

    @PostMapping("/bff/change-password")
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
        authentication: Authentication
    ): ChangePasswordResponse = try {
        // Validate current password
        dataRepository.validatePassword(authentication.name, request.currentPassword)
        
        // Change to new password
        dataRepository.changePassword(authentication.name, request.newPassword)
        
        ChangePasswordResponse(success = true, message = "Password changed successfully")
    } catch (e: InvalidPassword) {
        ChangePasswordResponse(success = false, message = "Current password is incorrect")
    } catch (e: Exception) {
        ChangePasswordResponse(success = false, message = "Failed to change password")
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

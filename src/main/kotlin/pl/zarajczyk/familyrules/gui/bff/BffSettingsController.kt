package pl.zarajczyk.familyrules.gui.bff

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
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

    @GetMapping("/bff/webhook-settings")
    fun getWebhookSettings(authentication: Authentication): WebhookSettingsResponse {
        return try {
            val user = usersService.get(authentication.name)
            val details = user.fetchDetails()
            
            WebhookSettingsResponse(
                success = true,
                webhookEnabled = details.webhookEnabled,
                webhookUrl = details.webhookUrl
            )
        } catch (_: Exception) {
            WebhookSettingsResponse(success = false, webhookEnabled = false, webhookUrl = null)
        }
    }

    @PostMapping("/bff/webhook-settings")
    fun updateWebhookSettings(
        @RequestBody request: UpdateWebhookSettingsRequest,
        authentication: Authentication
    ): UpdateWebhookSettingsResponse {
        return try {
            val user = usersService.get(authentication.name)
            user.updateWebhookSettings(request.webhookEnabled, request.webhookUrl)

            UpdateWebhookSettingsResponse(success = true, message = "Webhook settings updated successfully")
        } catch (_: Exception) {
            UpdateWebhookSettingsResponse(success = false, message = "Failed to update webhook settings")
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

data class WebhookSettingsResponse(
    val success: Boolean,
    val webhookEnabled: Boolean,
    val webhookUrl: String?
)

data class UpdateWebhookSettingsRequest(
    val webhookEnabled: Boolean,
    val webhookUrl: String?
)

data class UpdateWebhookSettingsResponse(
    val success: Boolean,
    val message: String
)

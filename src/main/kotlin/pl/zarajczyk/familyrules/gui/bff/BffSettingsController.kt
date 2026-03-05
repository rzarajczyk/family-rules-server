package pl.zarajczyk.familyrules.gui.bff

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.InvalidPassword
import pl.zarajczyk.familyrules.domain.UsersService
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import java.util.UUID

@RestController
class BffSettingsController(
    private val usersService: UsersService,
    private val usersRepository: UsersRepository
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
            val details = user.getDetails()
            
            WebhookSettingsResponse(
                success = true,
                webhookEnabled = details.webhookEnabled,
                webhookUrl = details.webhookUrl,
                webhookHistoryUntil = details.webhookHistoryUntil
            )
        } catch (_: Exception) {
            WebhookSettingsResponse(success = false, webhookEnabled = false, webhookUrl = null, webhookHistoryUntil = null)
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

    @GetMapping("/bff/webhook-call-history")
    fun getWebhookCallHistory(authentication: Authentication): WebhookCallHistoryResponse {
        return try {
            val user = usersService.get(authentication.name)
            val history = usersRepository.getWebhookCallHistory(user.asRef())
            val webhookHistoryUntil = user.getDetails().webhookHistoryUntil
            
            val calls = history.map { entry ->
                WebhookCallHistoryItem(
                    timestamp = entry.timestamp,
                    status = entry.status,
                    statusCode = entry.statusCode,
                    errorMessage = entry.errorMessage,
                    payload = entry.payload
                )
            }
            
            WebhookCallHistoryResponse(success = true, calls = calls, webhookHistoryUntil = webhookHistoryUntil)
        } catch (e: Exception) {
            WebhookCallHistoryResponse(success = false, calls = emptyList(), webhookHistoryUntil = null)
        }
    }

    @PostMapping("/bff/webhook-history/enable")
    fun enableWebhookHistory(authentication: Authentication): EnableWebhookHistoryResponse {
        return try {
            val user = usersService.get(authentication.name)
            usersRepository.deleteWebhookCallHistory(user.asRef())
            val until = System.currentTimeMillis() + 30 * 60 * 1000L
            usersRepository.updateWebhookHistoryUntil(user.asRef(), until)
            EnableWebhookHistoryResponse(success = true, webhookHistoryUntil = until)
        } catch (_: Exception) {
            EnableWebhookHistoryResponse(success = false, webhookHistoryUntil = null)
        }
    }

    @GetMapping("/bff/integration-api-settings")
    fun getIntegrationApiSettings(authentication: Authentication): IntegrationApiSettingsResponse {
        return try {
            val user = usersService.get(authentication.name)
            val details = user.getDetails()
            IntegrationApiSettingsResponse(success = true, token = details.integrationApiToken)
        } catch (_: Exception) {
            IntegrationApiSettingsResponse(success = false, token = null)
        }
    }

    @PostMapping("/bff/integration-api-settings/regenerate-token")
    fun regenerateIntegrationApiToken(authentication: Authentication): IntegrationApiSettingsResponse {
        return try {
            val user = usersService.get(authentication.name)
            val newToken = UUID.randomUUID().toString()
            user.updateIntegrationApiToken(newToken)
            IntegrationApiSettingsResponse(success = true, token = newToken)
        } catch (_: Exception) {
            IntegrationApiSettingsResponse(success = false, token = null)
        }
    }

    @PostMapping("/bff/integration-api-settings/revoke-token")
    fun revokeIntegrationApiToken(authentication: Authentication): IntegrationApiSettingsResponse {
        return try {
            val user = usersService.get(authentication.name)
            user.updateIntegrationApiToken(null)
            IntegrationApiSettingsResponse(success = true, token = null)
        } catch (_: Exception) {
            IntegrationApiSettingsResponse(success = false, token = null)
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
    val webhookUrl: String?,
    val webhookHistoryUntil: Long?
)

data class UpdateWebhookSettingsRequest(
    val webhookEnabled: Boolean,
    val webhookUrl: String?
)

data class UpdateWebhookSettingsResponse(
    val success: Boolean,
    val message: String
)

data class WebhookCallHistoryResponse(
    val success: Boolean,
    val calls: List<WebhookCallHistoryItem>,
    val webhookHistoryUntil: Long?
)

data class WebhookCallHistoryItem(
    val timestamp: Long,
    val status: String,
    val statusCode: Int?,
    val errorMessage: String?,
    val payload: String?
)

data class IntegrationApiSettingsResponse(
    val success: Boolean,
    val token: String?
)

data class EnableWebhookHistoryResponse(
    val success: Boolean,
    val webhookHistoryUntil: Long?
)


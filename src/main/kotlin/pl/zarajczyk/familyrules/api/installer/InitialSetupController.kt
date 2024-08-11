package pl.zarajczyk.familyrules.api.installer

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.*

@RestController
class InitialSetupController(private val dbConnector: DbConnector) {

    @PostMapping("/api/register-instance")
    fun registerInstance(
        @RequestBody data: RegisterInstanceRequest,
        @RequestHeader("Authorization") authHeader: String
    ): RegisterInstanceResponse = try {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validatePassword(auth.user, auth.pass)
        val token = dbConnector.setupNewInstance(auth.user, data.instanceName, data.os)
        RegisterInstanceResponse(RegisterInstanceStatus.SUCCESS, token)
    } catch (e: InvalidPassword) {
        RegisterInstanceResponse(RegisterInstanceStatus.INVALID_PASSWORD)
    } catch (e: InstanceAlreadyExists) {
        RegisterInstanceResponse(RegisterInstanceStatus.INSTANCE_ALREADY_EXISTS)
    } catch (e: IllegalInstanceName) {
        RegisterInstanceResponse(RegisterInstanceStatus.ILLEGAL_INSTANCE_NAME)
    }

    @PostMapping("/api/unregister-instance")
    fun unregisterInstance(
        @RequestBody data: UnregisterInstanceRequest,
        @RequestHeader("Authorization") authHeader: String
    ): UnregisterInstanceResponse = try {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validatePassword(auth.user, auth.pass)
        UnregisterInstanceResponse(UnregisterInstanceStatus.SUCCESS)
    } catch (e: InvalidPassword) {
        UnregisterInstanceResponse(UnregisterInstanceStatus.INVALID_PASSWORD)
    }

}

enum class SupportedOs {
    MAC_OS
}

data class RegisterInstanceRequest(
    val instanceName: String,
    val os: SupportedOs
)

enum class RegisterInstanceStatus {
    SUCCESS, INSTANCE_ALREADY_EXISTS, ILLEGAL_INSTANCE_NAME, INVALID_PASSWORD
}

data class RegisterInstanceResponse(
    val status: RegisterInstanceStatus,
    val token: String? = null
)

data class UnregisterInstanceRequest(
    val instanceName: String
)

enum class UnregisterInstanceStatus {
    SUCCESS, INVALID_PASSWORD
}

data class UnregisterInstanceResponse(
    val status: UnregisterInstanceStatus
)
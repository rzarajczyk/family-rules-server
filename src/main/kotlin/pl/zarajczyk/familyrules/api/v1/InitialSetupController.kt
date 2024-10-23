package pl.zarajczyk.familyrules.api.v1

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.*

@RestController
class InitialSetupController(private val dbConnector: DbConnector) {

    @PostMapping(value = ["/api/v1/register-instance"])
    fun registerInstance(
        @RequestBody data: RegisterInstanceRequest,
        @RequestHeader("Authorization") authHeader: String
    ): RegisterInstanceResponse = try {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validatePassword(auth.user, auth.pass)
        val result = dbConnector.setupNewInstance(auth.user, data.instanceName, data.clientType)
        RegisterInstanceResponse(
            RegisterInstanceStatus.SUCCESS,
            instanceId = result.instanceId.toString(),
            token = result.token
        )
    } catch (e: InvalidPassword) {
        RegisterInstanceResponse(RegisterInstanceStatus.INVALID_PASSWORD)
    } catch (e: InstanceAlreadyExists) {
        RegisterInstanceResponse(RegisterInstanceStatus.INSTANCE_ALREADY_EXISTS)
    } catch (e: IllegalInstanceName) {
        RegisterInstanceResponse(RegisterInstanceStatus.ILLEGAL_INSTANCE_NAME)
    }

    @PostMapping(value = ["/api/v1/unregister-instance"])
    fun unregisterInstance(
        @RequestBody data: UnregisterInstanceRequest,
        @RequestHeader("Authorization") authHeader: String
    ): UnregisterInstanceResponse = try {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validatePassword(auth.user, auth.pass)
        // TODO - should we remove data from the db?
        UnregisterInstanceResponse(UnregisterInstanceStatus.SUCCESS)
    } catch (e: InvalidPassword) {
        UnregisterInstanceResponse(UnregisterInstanceStatus.INVALID_PASSWORD)
    }

}

data class RegisterInstanceRequest(
    val instanceName: String,
    val clientType: String
)

enum class RegisterInstanceStatus {
    SUCCESS, INSTANCE_ALREADY_EXISTS, ILLEGAL_INSTANCE_NAME, INVALID_PASSWORD
}

data class RegisterInstanceResponse(
    val status: RegisterInstanceStatus,
    val instanceId: String? = null,
    val token: String? = null
)

data class UnregisterInstanceRequest(
    val instanceId: String
)

enum class UnregisterInstanceStatus {
    SUCCESS, INVALID_PASSWORD
}

data class UnregisterInstanceResponse(
    val status: UnregisterInstanceStatus
)
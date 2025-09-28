package pl.zarajczyk.familyrules.api.v2

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.*

@RestController
class V2InitialSetupController(private val dataRepository: DataRepository) {

    @PostMapping(value = ["/api/v2/register-instance"])
    fun registerInstance(
        @RequestBody data: RegisterInstanceRequest,
        @RequestHeader("Authorization") authHeader: String
    ): RegisterInstanceResponse = try {
        val auth = authHeader.decodeBasicAuth()
        dataRepository.validatePassword(auth.user, auth.pass)
        val result = dataRepository.setupNewInstance(auth.user, data.instanceName, data.clientType)
        RegisterInstanceResponse(
            RegisterInstanceStatus.SUCCESS,
            instanceId = result.instanceId.toString(),
            token = result.token
        )
    } catch (_: InvalidPassword) {
        RegisterInstanceResponse(RegisterInstanceStatus.INVALID_PASSWORD)
    } catch (_: InstanceAlreadyExists) {
        RegisterInstanceResponse(RegisterInstanceStatus.INSTANCE_ALREADY_EXISTS)
    } catch (_: IllegalInstanceName) {
        RegisterInstanceResponse(RegisterInstanceStatus.ILLEGAL_INSTANCE_NAME)
    }

    @PostMapping(value = ["/api/v2/unregister-instance"])
    fun unregisterInstance(): UnregisterInstanceResponse {
        // TODO - should we remove data from the db?
        return UnregisterInstanceResponse(UnregisterInstanceStatus.SUCCESS)
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

enum class UnregisterInstanceStatus {
    SUCCESS
}

data class UnregisterInstanceResponse(
    val status: UnregisterInstanceStatus
)
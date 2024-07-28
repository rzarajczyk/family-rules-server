package pl.zarajczyk.familyrules.setup

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.*

@RestController
class InitialSetupController(private val dbConnector: DbConnector) {

    @PostMapping("/setup")
    fun setup(
        @RequestBody data: InitialSetupRequest,
        @RequestHeader("Authorization", required = false) authHeader: String?
    ): InitialSetupResponse = try {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validatePassword(auth.user, auth.pass)
        val token = dbConnector.setupNewInstance(auth.user, data.instanceName)
        InitialSetupResponse(InitialSetupStatus.SUCCESS, token)
    } catch (e: InvalidPassword) {
        InitialSetupResponse(InitialSetupStatus.INVALID_PASSWORD)
    } catch (e: InstanceAlreadyExists) {
        InitialSetupResponse(InitialSetupStatus.INSTANCE_ALREADY_EXISTS)
    } catch (e: IllegalInstanceName) {
        InitialSetupResponse(InitialSetupStatus.ILLEGAL_INSTANCE_NAME)
    }

}

data class InitialSetupRequest(
    val instanceName: String
)

enum class InitialSetupStatus {
    SUCCESS, INSTANCE_ALREADY_EXISTS, ILLEGAL_INSTANCE_NAME, INVALID_PASSWORD
}

data class InitialSetupResponse(
    val status: InitialSetupStatus,
    val token: String? = null
)
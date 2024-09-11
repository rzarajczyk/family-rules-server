package pl.zarajczyk.familyrules.api.v1

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.DbConnector
import pl.zarajczyk.familyrules.shared.decodeBasicAuth

@RestController
class MigratorControllers(private val dbConnector: DbConnector) {

    @PostMapping("/api/v1/migrator/get-instance-id")
    fun getInstanceId(
        @RequestBody instanceName: String,
        @RequestHeader("Authorization", required = false) authHeader: String
    ): String {
        val auth = authHeader.decodeBasicAuth()
        val instanceId = dbConnector.validateInstanceToken(auth.user, instanceName, auth.pass)
        return instanceId.toString()
    }

}
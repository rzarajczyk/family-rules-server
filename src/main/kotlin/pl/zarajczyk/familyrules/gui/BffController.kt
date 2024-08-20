package pl.zarajczyk.familyrules.gui

import kotlinx.datetime.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.shared.*

@RestController
class BffController(private val dbConnector: DbConnector) {

    @PostMapping("/bff/login")
    fun login(@RequestHeader("Authorization") authHeader: String): LoginResponse =
        try {
            val auth = authHeader.decodeBasicAuth()
            val seed = randomSeed()
            val token = dbConnector.validatePasswordAndCreateOneTimeToken(auth.user, auth.pass, seed)
            LoginResponse(true, seed, token)
        } catch (e: InvalidPassword) {
            LoginResponse(false)
        }

    @GetMapping("/bff/status")
    fun status(
        @RequestParam("date") date: String,
        @RequestHeader("x-seed") seed: String,
        @RequestHeader("Authorization") authHeader: String
    ): StatusResponse = try {
        val day = LocalDate.parse(date)
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)
        val instances = dbConnector.getInstances(auth.user)
        StatusResponse(instances.map {
            val appUsageMap = dbConnector.getScreenTimeSeconds(it.id, day)
            InstanceStatus(
                instanceId = it.id,
                instanceName = it.name,
                screenTimeSeconds = appUsageMap.getOrDefault(DbConnector.TOTAL_TIME, 0L),
                appUsageSeconds = (appUsageMap - DbConnector.TOTAL_TIME).map { (k, v) -> AppUsage(k, v) },
                state = dbConnector.getInstanceState(it.id)?.toInstanceState() ?: InstanceState.empty()
            )
        })
    } catch (e: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    @PostMapping("/bff/state")
    fun state(
        @RequestHeader("x-seed") seed: String,
        @RequestHeader("Authorization") authHeader: String,
        @RequestParam("instanceName") instanceName: String,
        @RequestBody state: InstanceState
    ) {
        val auth = authHeader.decodeBasicAuth()
        dbConnector.validateOneTimeToken(auth.user, auth.pass, seed)
        val instanceId = dbConnector.getInstances(auth.user).find { it.name == instanceName }?.id
        if (instanceId == null)
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        dbConnector.setInstanceState(instanceId, state.toStateDto())
    }

    private fun StateDto.toInstanceState() = InstanceState(
        deviceState = this.deviceState,
        deviceStateCountdown = this.deviceStateCountdown
    )

    private fun InstanceState.toStateDto() = StateDto(
        deviceState = this.deviceState,
        deviceStateCountdown = this.deviceStateCountdown
    )


}

data class LoginResponse(
    val success: Boolean,
    val seed: String? = null,
    val token: String? = null
)

data class StatusResponse(
    val instances: List<InstanceStatus>
)

data class InstanceStatus(
    val instanceId: InstanceId,
    val instanceName: String,
    val screenTimeSeconds: Long,
    val appUsageSeconds: List<AppUsage>,
    val state: InstanceState
)

data class AppUsage(
    val name: String,
    val usageSeconds: Long
)

data class InstanceState(
    val deviceState: DeviceState,
    val deviceStateCountdown: Int
) {
    companion object {
        fun empty() = InstanceState(DeviceState.ACTIVE, 0)
    }
}
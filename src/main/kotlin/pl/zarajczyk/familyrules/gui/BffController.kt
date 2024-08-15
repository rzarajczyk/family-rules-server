package pl.zarajczyk.familyrules.gui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.shared.*
import java.time.Instant

@RestController
class BffController(private val dbConnector: DbConnector, private val securityService: SecurityService) {

    @PostMapping("/bff/login")
    fun login(@RequestHeader("Authorization") authHeader: String): LoginResponse =
        try {
            securityService.createOneTimeToken(authHeader).toLoginResponse()
        } catch (e: InvalidPassword) {
            LoginResponse(false)
        }

    fun OneTimeToken.toLoginResponse() = LoginResponse(true, seed, token)

    @GetMapping("/bff/status")
    fun status(
        @RequestParam("date") date: String,
        @RequestHeader("x-seed") seed: String,
        @RequestHeader("Authorization") authHeader: String
    ): StatusResponse = try {
        val day = LocalDate.parse(date)
        val user = securityService.validateOneTimeToken(authHeader, seed)
        val instances = dbConnector.getInstances(user)
        StatusResponse(instances.map {
            val appUsageMap = dbConnector.getScreenTimeSeconds(it.id, day)
            InstanceStatus(
                instanceId = it.id,
                instanceName = it.name,
                screenTimeSeconds = appUsageMap.getOrDefault(DbConnector.TOTAL_TIME, 0L),
                appUsageSeconds = appUsageMap - DbConnector.TOTAL_TIME,
                state = (dbConnector.getInstanceState(it.id) ?: StateDto.empty()).toInstanceState()
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
        val user = securityService.validateOneTimeToken(authHeader, seed)
        val instanceId = dbConnector.getInstances(user).find { it.name == instanceName }?.id
        if (instanceId == null)
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        dbConnector.setInstanceState(instanceId, state.toStateDto())
    }

    private fun StateDto.toInstanceState() = InstanceState(
        lockedSince = this.lockedSince?.toJavaInstant(),
        loggedOutSince = this.loggedOutSince?.toJavaInstant()
    )

    private fun InstanceState.toStateDto() = StateDto(
        lockedSince = this.lockedSince?.toKotlinInstant(),
        loggedOutSince = this.loggedOutSince?.toKotlinInstant()
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
    val appUsageSeconds: Map<String, Long>,
    val state: InstanceState
)

data class InstanceState(
    val lockedSince: Instant?,
    val loggedOutSince: Instant?
)
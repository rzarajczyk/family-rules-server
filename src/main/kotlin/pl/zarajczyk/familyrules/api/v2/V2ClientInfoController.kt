package pl.zarajczyk.familyrules.api.v2

import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set
import pl.zarajczyk.familyrules.util.pngBase64ToWebP

@RestController
class V2ClientInfoController(
    private val devicesService: DevicesService,
) {

    @PostMapping(value = ["/api/v2/launch", "/api/v2/client-info"])
    fun clientInfo(@RequestBody request: ClientInfoRequest, authentication: Authentication): ClientInfoResponse {
        val device = devicesService.get(authentication)
        val currentCapabilities = device.getDetails().capabilities
        val newCapabilities = request.capabilities ?: currentCapabilities

        try {
            validateForceReportPushCapabilities(newCapabilities)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.message)
        }

        if (request.pushToken != null && deviceForceReportPushCapability(newCapabilities) == null) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "pushToken requires a force-report push capability",
            )
        }

        val newKnownApps = request.knownApps?.mapValues { it.value.toDto() } ?: emptyMap()
        var update = DeviceDetailsUpdateDto(
            clientVersion = set(request.version),
            clientTimezoneOffsetSeconds = set(request.timezoneOffsetSeconds ?: 0L),
            reportIntervalSeconds = set(request.reportIntervalSeconds ?: 60L),
            knownApps = set(newKnownApps),
            availableDeviceStates = set(request.availableStates.map { it.toDto() }),
        )
        if (request.capabilities != null) {
            update = update.copy(capabilities = set(request.capabilities))
        }

        update = applyForceReportPushRegistration(
            update = update,
            capabilitiesUpdated = request.capabilities != null,
            newCapabilities = newCapabilities,
            pushToken = request.pushToken,
        )

        device.update(update)
        return ClientInfoResponse()
    }

    private fun applyForceReportPushRegistration(
        update: DeviceDetailsUpdateDto,
        capabilitiesUpdated: Boolean,
        newCapabilities: List<String>,
        pushToken: String?,
    ): DeviceDetailsUpdateDto {
        var result = update
        if (capabilitiesUpdated && deviceForceReportPushCapability(newCapabilities) == null) {
            result = result.copy(
                pushToken = set(null),
                pushTokenUpdatedAt = set(null),
            )
        }
        if (pushToken != null) {
            val trimmed = pushToken.trim()
            if (trimmed.isEmpty()) {
                throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "pushToken must not be blank")
            }
            result = result.copy(
                pushToken = set(trimmed),
                pushTokenUpdatedAt = set(Clock.System.now()),
            )
        }
        return result
    }

    private fun DeviceStateTypeRequest.toDto() = DeviceStateTypeDto(
        deviceState = deviceState,
        title = title,
        icon = icon,
        description = description,
        arguments = arguments?.mapNotNull { it.toDeviceStateArgument() }?.toSet() ?: emptySet()
    )

    private fun App.toDto() = AppDto(
        appName = appName,
        iconWebp = iconBase64Png?.let { pngBase64ToWebP(it) }
    )
}

private fun String.toDeviceStateArgument() =
    try {
        DeviceStateArgument.valueOf(this)
    } catch (_: Exception) {
        val logger = LoggerFactory.getLogger(javaClass)
        logger.warn("Ignoring DeviceStateArgument $this")
        null
    }

data class ClientInfoRequest(
    val version: String,
    val availableStates: List<DeviceStateTypeRequest>,
    val timezoneOffsetSeconds: Long?,
    val reportIntervalSeconds: Long?,
    val knownApps: Map<String, App>?,
    val capabilities: List<String>? = null,
    val pushToken: String? = null,
)

data class ClientInfoResponse(
    val status: String = "ok"
)

data class DeviceStateTypeRequest(
    val deviceState: String,
    val title: String,
    val icon: String?,
    val description: String?,
    val arguments: Set<String>?
)

data class App(
    val appName: String,
    val iconBase64Png: String?
)

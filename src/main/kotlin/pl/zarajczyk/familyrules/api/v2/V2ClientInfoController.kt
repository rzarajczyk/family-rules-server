package pl.zarajczyk.familyrules.api.v2

import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.ValueUpdate
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set

@RestController
class V2ClientInfoController(private val devicesService: DevicesService) {

    @PostMapping(value = ["/api/v2/launch", "/api/v2/client-info"])
    fun clientInfo(@RequestBody request: ClientInfoRequest, authentication: Authentication): ClientInfoResponse {
        val device = devicesService.get(authentication)
        device.update(DeviceDetailsUpdateDto(
            clientVersion = set(request.version),
            clientTimezoneOffsetSeconds = set(request.timezoneOffsetSeconds ?: 0L),
            reportIntervalSeconds = set(request.reportIntervalSeconds ?: 60L),
            knownApps = set(request.knownApps?.mapValues { it.value.toDto() } ?: emptyMap()),
            availableDeviceStates = set(request.availableStates.map { it.toDto() })

        ))
        return ClientInfoResponse()
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
        iconBase64Png = iconBase64Png
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
    val knownApps: Map<String, App>?
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
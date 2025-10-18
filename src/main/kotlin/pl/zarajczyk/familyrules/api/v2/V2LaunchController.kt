package pl.zarajczyk.familyrules.api.v2

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.DataRepository
import pl.zarajczyk.familyrules.shared.DescriptiveDeviceStateDto
import pl.zarajczyk.familyrules.shared.DeviceState
import pl.zarajczyk.familyrules.shared.findAuthenticatedInstance
import pl.zarajczyk.familyrules.shared.AppDto

@RestController
class V2LaunchController(private val dataRepository: DataRepository) {
    @PostMapping(value = ["/api/v2/launch", "/api/v2/client-info"])
    fun launch(@RequestBody request: LaunchRequest, authentication: Authentication) {
        val instanceRef = dataRepository.findAuthenticatedInstance(authentication)
        dataRepository.updateClientInformation(
            instance = instanceRef,
            version = request.version,
            timezoneOffsetSeconds = request.timezoneOffsetSeconds ?: 0,
            reportIntervalSeconds = request.reportIntervalSeconds ?: 60,
            knownApps = request.knownApps?.mapValues { it.value.toDto() } ?: emptyMap()
        )
        dataRepository.updateAvailableDeviceStates(instanceRef, request.availableStates.map { it.toDto() })
    }

    private fun AvailableDeviceState.toDto() = DescriptiveDeviceStateDto(
        deviceState = deviceState,
        title = title,
        icon = icon,
        description = description
    )

    private fun App.toDto() = AppDto(
        appName = appName,
        iconBase64Png = iconBase64Png
    )
}

data class LaunchRequest(
    val version: String,
    val availableStates: List<AvailableDeviceState>,
    val timezoneOffsetSeconds: Int?,
    val reportIntervalSeconds: Int?,
    val knownApps: Map<String, App>?
)

data class AvailableDeviceState(
    val deviceState: DeviceState,
    val title: String,
    val icon: String?,
    val description: String?
)

data class App(
    val appName: String,
    val iconBase64Png: String?
)
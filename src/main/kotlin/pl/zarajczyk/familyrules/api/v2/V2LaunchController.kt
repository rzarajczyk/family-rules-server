package pl.zarajczyk.familyrules.api.v2

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.DataRepository
import pl.zarajczyk.familyrules.shared.DescriptiveDeviceStateDto
import pl.zarajczyk.familyrules.shared.DeviceState
import pl.zarajczyk.familyrules.shared.findAuthenticatedInstance

@RestController
class V2LaunchController(private val dataRepository: DataRepository) {
    @PostMapping(value = ["/api/v2/launch"])
    fun launch(@RequestBody request: LaunchRequest, authentication: Authentication) {
        val instanceRef = dataRepository.findAuthenticatedInstance(authentication)
        dataRepository.updateClientInformation(instanceRef, request.version, request.timezoneOffsetSeconds)
        dataRepository.updateAvailableDeviceStates(instanceRef, request.availableStates.map { it.toDto() })
    }

    private fun AvailableDeviceState.toDto() = DescriptiveDeviceStateDto(
        deviceState = deviceState,
        title = title,
        icon = icon,
        description = description
    )
}

data class LaunchRequest(
    val version: String,
    val availableStates: List<AvailableDeviceState>,
    val timezoneOffsetSeconds: Int = 0
)

data class AvailableDeviceState(
    val deviceState: DeviceState,
    val title: String,
    val icon: String?,
    val description: String?
)
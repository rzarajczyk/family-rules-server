package pl.zarajczyk.familyrules.api.v2

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.*

@RestController
class V2LaunchController(private val dbConnector: DbConnector) {
    @PostMapping(value = ["/api/v2/launch"])
    fun launch(@RequestBody request: LaunchRequest, authentication: Authentication) {
        val instanceId = authentication.principal as InstanceId
        dbConnector.updateClientInformation(instanceId, request.version, request.timezoneOffsetSeconds)
        dbConnector.updateAvailableDeviceStates(instanceId, request.availableStates.map { it.toDto() })
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
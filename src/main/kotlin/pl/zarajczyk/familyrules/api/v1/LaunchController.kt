package pl.zarajczyk.familyrules.api.v1

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.*

@RestController
class LaunchController(private val dbConnector: DbConnector) {
    @PostMapping(value = ["/api/v1/launch"])
    fun launch(
        @RequestBody request: LaunchRequest,
        @RequestHeader("Authorization") authHeader: String
    ) {
        val auth = authHeader.decodeBasicAuth()
        val instanceId = InstanceId.fromString(request.instanceId)
        dbConnector.validateInstanceToken(auth.user, instanceId, auth.pass)

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
    val instanceId: String,
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
package pl.zarajczyk.familyrules.api.v2

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.shared.*

@RestController
class V2ClientInfoController(private val dataRepository: DataRepository) {
    @PostMapping(value = ["/api/v2/launch", "/api/v2/client-info"])
    fun clientInfo(@RequestBody request: ClientInfoRequest, authentication: Authentication): ClientInfoResponse {
        val instanceRef = dataRepository.findAuthenticatedInstance(authentication)
        dataRepository.updateClientInformation(
            instance = instanceRef,
            clientInfo = ClientInfoDto(
                version = request.version,
                timezoneOffsetSeconds = request.timezoneOffsetSeconds ?: 0,
                reportIntervalSeconds = request.reportIntervalSeconds ?: 60,
                knownApps = request.knownApps?.mapValues { it.value.toDto() } ?: emptyMap(),
                states = request.availableStates.map { it.toDto() }
            )
        )
        val instance = dataRepository.getInstance(instanceRef)
        val groupId = instance.associatedAppGroupId
        if (groupId.isNullOrBlank()) {
            return ClientInfoResponse(monitoredApps = emptyMap())
        }

        val memberships = dataRepository.getAppGroupMemberships(instanceRef)
            .filter { it.groupId == groupId }

        val apps = memberships.associate { membership ->
            val known = instance.knownApps[membership.appPath]
            membership.appPath to App(
                appName = known?.appName ?: membership.appPath,
                iconBase64Png = known?.iconBase64Png
            )
        }

        return ClientInfoResponse(monitoredApps = apps)
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

data class ClientInfoRequest(
    val version: String,
    val availableStates: List<AvailableDeviceState>,
    val timezoneOffsetSeconds: Int?,
    val reportIntervalSeconds: Int?,
    val knownApps: Map<String, App>?
)

data class ClientInfoResponse(
    val monitoredApps: Map<String, App>
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
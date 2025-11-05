package pl.zarajczyk.familyrules.api.v2

import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.*

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
            return ClientInfoResponse(restrictedApps = emptyMap())
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

        return ClientInfoResponse(restrictedApps = apps)
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
    val timezoneOffsetSeconds: Int?,
    val reportIntervalSeconds: Int?,
    val knownApps: Map<String, App>?
)

data class ClientInfoResponse(
    val restrictedApps: Map<String, App>
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
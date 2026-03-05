package pl.zarajczyk.familyrules.gui.bff

import kotlinx.datetime.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.*
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set

@RestController
class BffOverviewController(
    private val devicesService: DevicesService,
    private val stateService: StateService,
    private val deviceStateService: DeviceStateService,
    private val appGroupService: AppGroupService,
    private val usersService: UsersService
) {

//    companion object {
//        private val DEFAULT_ICON = Icon(
//            type = "image/png",
//            data = Base64.getEncoder().encodeToString(
//                BffOverviewController::class.java.getResourceAsStream("/gui/default-icon.png")!!.readAllBytes()
//            )
//        )
//    }

    @GetMapping("/bff/status")
    fun status(
        @RequestParam("date") date: String,
        authentication: Authentication,
    ): StatusResponse = try {
        val user = usersService.get(authentication.name)
        val day = LocalDate.parse(date)
        val devices = devicesService.getAllDevices(user)
        val appGroups = appGroupService.listAllAppGroups(user)
        val appGroupsDetails = appGroups.associateWith { it.getDetails() }

        // Pre-build reverse index: (deviceId, appTechnicalId) → list of AppGroupWithColor
        val appGroupIndex = mutableMapOf<Pair<String, String>, MutableList<AppGroupWithColor>>()
        for ((group, groupDto) in appGroupsDetails) {
            val colorInfo = AppGroupColorPalette.getColorInfo(groupDto.color)
            val groupWithColor = AppGroupWithColor(
                id = groupDto.id,
                name = groupDto.name,
                color = groupDto.color,
                textColor = colorInfo?.text ?: "#000000",
            )
            for ((deviceId, appIds) in group.asRef().details.members) {
                for (appId in appIds) {
                    appGroupIndex.getOrPut(deviceId to appId) { mutableListOf() }.add(groupWithColor)
                }
            }
        }

        StatusResponse(devices.map { device ->
            val screenTimeDto = device.getScreenTimeReport(day)
            val deviceDetails = device.getDetails()
            val state = stateService.calculateCurrentDeviceState(deviceDetails)
            val availableStates = deviceDetails.availableDeviceStates
            val deviceIdStr = deviceDetails.deviceId.toString()

            Instance(
                instanceId = deviceDetails.deviceId,
                instanceName = deviceDetails.deviceName,
                screenTimeSeconds = screenTimeDto.screenTimeSeconds,
                screenTimeHistogram = screenTimeDto.screenTimeHistogram,
                appUsageSeconds = screenTimeDto.applicationsSeconds
                    .map { (appTechnicalId, v) ->
                        val knownApp = deviceDetails.knownApps[appTechnicalId]
                        AppUsage(
                            name = appTechnicalId,
                            path = appTechnicalId,
                            usageSeconds = v,
                            appName = knownApp?.appName,
                            iconBase64 = knownApp?.iconBase64Png,
                            appGroups = appGroupIndex[deviceIdStr to appTechnicalId] ?: emptyList(),
                            online = appTechnicalId in screenTimeDto.onlineApps
                        )
                    }.sortedByDescending { it.usageSeconds },
                forcedDeviceState = availableStates
                    .flatMap { it.toDeviceStateDescriptions(appGroupsDetails.values) }
                    .firstOrNull { it.isEqualTo(state.forcedState) },
                automaticDeviceState = availableStates
                    .flatMap { it.toDeviceStateDescriptions(appGroupsDetails.values) }
                    .firstOrNull { it.isEqualTo(state.automaticState) }
                    ?: throw RuntimeException("Instance ≪${deviceDetails.deviceId}≫ doesn't have automatic state ≪${state.automaticState}≫"),
                online = screenTimeDto.online,
                icon = deviceDetails.getIcon(),
                availableAppGroups = appGroupsDetails.values.toList(),
            )
        })
    } catch (_: InvalidPassword) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun DeviceStateDescriptionResponse.isEqualTo(other: DeviceStateDto?) =
        this.deviceState == other?.deviceState && this.extra == other?.extra

    private fun DeviceDetailsDto.getIcon() = if (iconType != null && iconData != null) {
        Icon(type = iconType, data = iconData)
    } else {
        Icon(type = null, data = null)
    }

    @GetMapping("/bff/instance-info")
    fun getInstanceInfo(
        @RequestParam("instanceId") deviceId: DeviceId
    ): InstanceInfoResponse {
        val device = devicesService.get(deviceId)
        val details = device.getDetails()
        return InstanceInfoResponse(
            instanceId = deviceId,
            instanceName = details.deviceName,
            forcedDeviceState = details.forcedDeviceState,
            clientType = details.clientType,
            clientVersion = details.clientVersion,
            clientTimezoneOffsetSeconds = details.clientTimezoneOffsetSeconds
        )
    }


    @GetMapping("/bff/instance-edit-info")
    fun getInstanceEditInfo(
        @RequestParam("instanceId") deviceId: DeviceId,
        authentication: Authentication
    ): InstanceEditInfo {
        val device = devicesService.get(deviceId)
        val details = device.getDetails()
        val user = usersService.get(authentication.name)
        val appGroups = appGroupService.listAllAppGroups(user).map { it.getDetails() }
        return InstanceEditInfo(
            instanceName = details.deviceName,
            icon = details.getIcon(),
            appGroups = details.appGroups,
            availableAppGroups = appGroups
        )
    }

    @PostMapping("/bff/instance-edit-info")
    fun setInstanceEditInfo(
        @RequestParam("instanceId") deviceId: DeviceId,
        @RequestBody data: InstanceEditInfo
    ) {
        val device = devicesService.get(deviceId)
        device.update(
            DeviceDetailsUpdateDto(
                deviceName = ValueUpdate.set(data.instanceName),
                iconData = data.icon?.let { ValueUpdate.set(data.icon.data) } ?: ValueUpdate.leaveUnchanged(),
                iconType = data.icon?.let { ValueUpdate.set(data.icon.type) } ?: ValueUpdate.leaveUnchanged(),
                appGroups = ValueUpdate.set(data.appGroups)
            ))
    }

    data class InstanceEditInfo(
        val instanceName: String,
        val icon: Icon?,
        val appGroups: AppGroupsDto = AppGroupsDto.empty(),
        val availableAppGroups: List<AppGroupDetails> = emptyList()
    )

    @GetMapping("/bff/instance-state")
    fun getInstanceState(
        @RequestParam("instanceId") deviceId: DeviceId,
        authentication: Authentication,
    ): InstanceStateResponse {
        val device = devicesService.get(deviceId)
        val appGroups = usersService.get(authentication.name).let { user ->
            appGroupService.listAllAppGroups(user).map { it.getDetails() }
        }
        val deviceDetails = device.getDetails()
        return InstanceStateResponse(
            instanceId = deviceId,
            instanceName = deviceDetails.deviceName,
            forcedDeviceState = deviceDetails.forcedDeviceState,
            availableStates = deviceDetails.availableDeviceStates
                .flatMap { it.toDeviceStateDescriptions(appGroups) }
        )
    }

    @PostMapping("/bff/instance-state")
    fun setInstanceState(
        @RequestParam("instanceId") deviceId: DeviceId,
        @RequestBody data: ForcedInstanceState
    ) {
        val forcedDeviceState = data.forcedDeviceState.emptyToNull()?.let {
            DeviceStateDto(
                deviceState = it,
                extra = data.extra?.emptyToNull()
            )
        }

        val device = devicesService.get(deviceId)
        device.update(DeviceDetailsUpdateDto(
            forcedDeviceState = set(forcedDeviceState)
        ))
    }

    @PostMapping("/bff/delete-instance")
    fun deleteInstance(
        @RequestParam("instanceId") deviceId: DeviceId
    ) = devicesService.get(deviceId).delete()

    private fun DeviceStateTypeDto.toDeviceStateDescriptions(appGroups: Collection<AppGroupDetails>) =
        deviceStateService.createActualInstances(this, appGroups)
            .map {
                DeviceStateDescriptionResponse(
                    deviceState = it.deviceState,
                    title = it.title,
                    icon = it.icon,
                    description = it.description,
                    extra = it.extra
                )
            }

}

private fun String?.emptyToNull(): String? = if (this.isNullOrBlank()) null else this

data class InstanceStateResponse(
    val instanceId: DeviceId,
    val instanceName: String,
    val forcedDeviceState: DeviceStateDto?,
    val availableStates: List<DeviceStateDescriptionResponse>
)

data class InstanceInfoResponse(
    val instanceId: DeviceId,
    val instanceName: String,
    val forcedDeviceState: DeviceStateDto?,
    val clientType: String,
    val clientVersion: String,
    val clientTimezoneOffsetSeconds: Long,
)

data class DeviceStateDescriptionResponse(
    val deviceState: String,
    val title: String,
    val icon: String?,
    val description: String?,
    val extra: String?
)


data class StatusResponse(
    val instances: List<Instance>
)

data class Instance(
    val instanceId: DeviceId,
    val instanceName: String,
    val icon: Icon,
    val screenTimeSeconds: Long,
    val screenTimeHistogram: Map<String, Long>,
    val appUsageSeconds: List<AppUsage>,
    val automaticDeviceState: DeviceStateDescriptionResponse,
    val forcedDeviceState: DeviceStateDescriptionResponse?,
    val online: Boolean,
    val availableAppGroups: List<AppGroupDetails>,
)

data class Icon(
    val type: String?,
    val data: String?
)

data class AppUsage(
    val name: String,
    val path: String,
    val usageSeconds: Long,
    val appName: String? = null,
    val iconBase64: String? = null,
    val online: Boolean,
    val appGroups: List<AppGroupWithColor> = emptyList()
)

data class ForcedInstanceState(
    val forcedDeviceState: String?,
    val extra: String?
)
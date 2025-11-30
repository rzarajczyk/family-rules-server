package pl.zarajczyk.familyrules.gui.bff

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set
import java.util.*

@RestController
class BffGroupStatesController(
    private val usersService: UsersService,
    private val appGroupService: AppGroupService,
    private val groupStateService: GroupStateService,
    private val devicesService: DevicesService
) {

    @PostMapping("/bff/app-groups/{groupId}/apply-state/{stateId}")
    fun applyGroupState(
        @PathVariable groupId: String,
        @PathVariable stateId: String,
        authentication: Authentication
    ): ApplyGroupStateResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        val state = groupStateService.getGroupState(appGroup, stateId)
        val details = state.fetchDetails()
        
        // Apply each device state from the group state
        // null means "Automatic" (set forcedDeviceState to null)
        // missing from map means "Do not change" (skip the device)
        details.deviceStates.forEach { (deviceId, deviceState) ->
            val device = devicesService.get(deviceId)
            device.update(DeviceDetailsUpdateDto(
                forcedDeviceState = set(deviceState)
            ))
        }
        
        return ApplyGroupStateResponse(success = true, appliedDevices = details.deviceStates.size)
    }

    @GetMapping("/bff/app-groups/{groupId}/states")
    fun getGroupStates(
        @PathVariable groupId: String,
        authentication: Authentication
    ): GetGroupStatesResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        val states = groupStateService.listAllGroupStates(appGroup)
            .map { state ->
                val details = state.fetchDetails()
                GroupStateDto(
                    id = details.id,
                    name = details.name,
                    deviceStates = details.deviceStates.mapKeys { it.key.toString() }
                )
            }
        return GetGroupStatesResponse(states)
    }

    @PostMapping("/bff/app-groups/{groupId}/states")
    fun createGroupState(
        @PathVariable groupId: String,
        @RequestBody request: CreateGroupStateRequest,
        authentication: Authentication
    ): CreateGroupStateResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        
        val deviceStatesMap = request.deviceStates.mapKeys { UUID.fromString(it.key) }
        val state = groupStateService.createGroupState(appGroup, request.name, deviceStatesMap)
        val details = state.fetchDetails()
        
        return CreateGroupStateResponse(
            state = GroupStateDto(
                id = details.id,
                name = details.name,
                deviceStates = details.deviceStates.mapKeys { it.key.toString() }
            )
        )
    }

    @PutMapping("/bff/app-groups/{groupId}/states/{stateId}")
    fun updateGroupState(
        @PathVariable groupId: String,
        @PathVariable stateId: String,
        @RequestBody request: UpdateGroupStateRequest,
        authentication: Authentication
    ): UpdateGroupStateResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        val state = groupStateService.getGroupState(appGroup, stateId)
        
        val deviceStatesMap = request.deviceStates.mapKeys { UUID.fromString(it.key) }
        state.update(request.name, deviceStatesMap)
        
        return UpdateGroupStateResponse(success = true)
    }

    @DeleteMapping("/bff/app-groups/{groupId}/states/{stateId}")
    fun deleteGroupState(
        @PathVariable groupId: String,
        @PathVariable stateId: String,
        authentication: Authentication
    ): DeleteGroupStateResponse {
        val user = usersService.get(authentication.name)
        val appGroup = appGroupService.get(user, groupId)
        val state = groupStateService.getGroupState(appGroup, stateId)
        state.delete()
        
        return DeleteGroupStateResponse(success = true)
    }

    @GetMapping("/bff/app-groups/{groupId}/devices-for-states")
    fun getDevicesForStates(
        @PathVariable groupId: String,
        authentication: Authentication
    ): GetDevicesForStatesResponse {
        val user = usersService.get(authentication.name)
        val devices = devicesService.getAllDevices(user)
        
        val deviceInfos = devices.map { device ->
            val details = device.fetchDetails()
            DeviceForStateDto(
                deviceId = details.deviceId.toString(),
                deviceName = details.deviceName,
                availableStates = details.availableDeviceStates.map { stateType ->
                    DeviceStateTypeResponse(
                        deviceState = stateType.deviceState,
                        title = stateType.title,
                        icon = stateType.icon,
                        description = stateType.description
                    )
                }
            )
        }
        
        return GetDevicesForStatesResponse(devices = deviceInfos)
    }
}

data class GetGroupStatesResponse(
    val states: List<GroupStateDto>
)

data class GroupStateDto(
    val id: String,
    val name: String,
    val deviceStates: Map<String, DeviceStateDto?>
)

data class CreateGroupStateRequest(
    val name: String,
    val deviceStates: Map<String, DeviceStateDto?>
)

data class CreateGroupStateResponse(
    val state: GroupStateDto
)

data class UpdateGroupStateRequest(
    val name: String,
    val deviceStates: Map<String, DeviceStateDto?>
)

data class UpdateGroupStateResponse(
    val success: Boolean
)

data class DeleteGroupStateResponse(
    val success: Boolean
)

data class GetDevicesForStatesResponse(
    val devices: List<DeviceForStateDto>
)

data class DeviceForStateDto(
    val deviceId: String,
    val deviceName: String,
    val availableStates: List<DeviceStateTypeResponse>
)

data class DeviceStateTypeResponse(
    val deviceState: String,
    val title: String,
    val icon: String?,
    val description: String?
)

data class ApplyGroupStateResponse(
    val success: Boolean,
    val appliedDevices: Int
)

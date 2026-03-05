package pl.zarajczyk.familyrules.api.integration

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.domain.*
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto
import pl.zarajczyk.familyrules.domain.webhook.WebhookQueue

@RestController
@RequestMapping("/integration-api/v1")
class IntegrationAppGroupsController(
    private val usersService: UsersService,
    private val appGroupService: AppGroupService,
    private val groupStateService: GroupStateService,
    private val devicesService: DevicesService,
    private val stateService: StateService,
    private val webhookQueue: WebhookQueue,
) {
    private val LOGGER = LoggerFactory.getLogger(javaClass)

    @GetMapping("/app-groups")
    fun listAppGroups(authentication: Authentication): IntegrationAppGroupsResponse {
        val user = usersService.get(authentication.name)
        val groups = appGroupService.listAllAppGroups(user)

        val allDevices = devicesService.getAllDevices(user)
        val deviceForcedStates: Map<DeviceId, DeviceStateDto?> =
            allDevices.associate { device ->
                val details = device.getDetails()
                details.deviceId to stateService.calculateCurrentDeviceState(details).forcedState
            }

        return IntegrationAppGroupsResponse(
            appGroups = groups.map { appGroup ->
                val details = appGroup.fetchDetails()
                val states = groupStateService.listAllGroupStates(appGroup)
                val stateDetails = states.map { it.fetchDetails() }
                val currentState = calculateCurrentGroupState(appGroup, stateDetails, deviceForcedStates)
                IntegrationAppGroupDto(
                    id = details.id,
                    name = details.name,
                    color = details.color,
                    availableStates = stateDetails.map { IntegrationGroupStateDto(id = it.id, name = it.name) },
                    currentState = currentState,
                )
            }
        )
    }

    @GetMapping("/app-groups/{groupId}/state")
    fun getAppGroupState(
        @PathVariable groupId: String,
        authentication: Authentication,
    ): IntegrationGroupCurrentStateResponse {
        val user = usersService.get(authentication.name)
        val appGroup = try {
            appGroupService.get(user, groupId)
        } catch (_: AppGroupNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "App group not found")
        }

        val allDevices = devicesService.getAllDevices(user)
        val deviceForcedStates: Map<DeviceId, DeviceStateDto?> =
            allDevices.associate { device ->
                val details = device.getDetails()
                details.deviceId to stateService.calculateCurrentDeviceState(details).forcedState
            }

        val stateDetails = groupStateService.listAllGroupStates(appGroup).map { it.fetchDetails() }
        val currentState = calculateCurrentGroupState(appGroup, stateDetails, deviceForcedStates)

        return IntegrationGroupCurrentStateResponse(
            groupId = groupId,
            currentState = currentState,
            availableStates = stateDetails.map { IntegrationGroupStateDto(id = it.id, name = it.name) },
        )
    }

    @PostMapping("/app-groups/{groupId}/apply-state/{stateId}")
    fun applyGroupState(
        @PathVariable groupId: String,
        @PathVariable stateId: String,
        authentication: Authentication,
    ): IntegrationApplyStateResponse {
        val user = usersService.get(authentication.name)
        val appGroup = try {
            appGroupService.get(user, groupId)
        } catch (_: AppGroupNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "App group not found")
        }
        val state = try {
            groupStateService.getGroupState(appGroup, stateId)
        } catch (_: GroupStateNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Group state not found")
        }

        groupStateService.apply(state)

        // Trigger a webhook push so HA receives the updated state promptly
        webhookQueue.enqueue(authentication.name)

        val details = state.fetchDetails()
        LOGGER.info("Applied group state ${details.name} to group $groupId")
        return IntegrationApplyStateResponse(
            success = true,
            appliedStateName = details.name,
            appliedDevices = details.deviceStates.size,
        )
    }

    private fun calculateCurrentGroupState(
        appGroup: AppGroup,
        stateDetails: List<GroupStateDetails>,
        deviceForcedStates: Map<DeviceId, DeviceStateDto?>,
    ): IntegrationCurrentGroupState {
        val groupDeviceIds = stateDetails.flatMap { it.deviceStates.keys }.toSet()

        if (groupDeviceIds.isEmpty()) {
            return IntegrationCurrentGroupState(kind = "automatic", label = "Automatic", stateId = null)
        }

        // A named state matches only when every device has a forced state equal to the definition
        val matched = stateDetails.firstOrNull { details ->
            details.deviceStates.all { (deviceId, expectedState) ->
                deviceForcedStates[deviceId] == expectedState
            }
        }

        if (matched != null) {
            return IntegrationCurrentGroupState(kind = "named", label = matched.name, stateId = matched.id)
        }

        val allAutomatic = groupDeviceIds.all { deviceId -> deviceForcedStates[deviceId] == null }

        return if (allAutomatic) {
            IntegrationCurrentGroupState(kind = "automatic", label = "Automatic", stateId = null)
        } else {
            IntegrationCurrentGroupState(kind = "different", label = "Different", stateId = null)
        }
    }
}

data class IntegrationAppGroupsResponse(
    val appGroups: List<IntegrationAppGroupDto>,
)

data class IntegrationAppGroupDto(
    val id: String,
    val name: String,
    val color: String,
    val availableStates: List<IntegrationGroupStateDto>,
    val currentState: IntegrationCurrentGroupState,
)

data class IntegrationGroupStateDto(
    val id: String,
    val name: String,
)

data class IntegrationCurrentGroupState(
    val kind: String,   // "named" | "automatic" | "different"
    val label: String,
    val stateId: String?,
)

data class IntegrationGroupCurrentStateResponse(
    val groupId: String,
    val currentState: IntegrationCurrentGroupState,
    val availableStates: List<IntegrationGroupStateDto>,
)

data class IntegrationApplyStateResponse(
    val success: Boolean,
    val appliedStateName: String,
    val appliedDevices: Int,
)

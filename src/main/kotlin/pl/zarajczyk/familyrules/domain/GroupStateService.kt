package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.*
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set
import java.util.*

@Service
class GroupStateService(
    private val appGroupRepository: AppGroupRepository,
    private val devicesService: DevicesService,
) {
    fun createGroupState(appGroup: AppGroup, name: String, deviceStates: Map<DeviceId, DeviceStateDto?>): GroupState {
        val stateId = UUID.randomUUID().toString()
        val groupStateRef = appGroupRepository.createGroupState(appGroup.asRef(), stateId, name, deviceStates)
        return RefBasedGroupState(groupStateRef, appGroupRepository)
    }

    fun getGroupState(appGroup: AppGroup, stateId: String): GroupState {
        val groupStateRef = appGroupRepository.getGroupState(appGroup.asRef(), stateId)
            ?: throw GroupStateNotFoundException(stateId)
        return RefBasedGroupState(groupStateRef, appGroupRepository)
    }

    fun listAllGroupStates(appGroup: AppGroup): List<GroupState> {
        return appGroupRepository.getAllGroupStates(appGroup.asRef())
            .map { RefBasedGroupState(it, appGroupRepository) }
    }

    fun apply(state: GroupState) {
        val details = state.fetchDetails()
        details.deviceStates.forEach { (deviceId, deviceState) ->
            devicesService.get(deviceId).update(DeviceDetailsUpdateDto(forcedDeviceState = set(deviceState)))
        }
    }
}

interface GroupState {
    fun asRef(): GroupStateRef
    fun fetchDetails(): GroupStateDetails
    fun update(name: String, deviceStates: Map<DeviceId, DeviceStateDto?>)
    fun delete()
}

data class RefBasedGroupState(
    val groupStateRef: GroupStateRef,
    private val appGroupRepository: AppGroupRepository
) : GroupState {
    override fun asRef(): GroupStateRef = groupStateRef

    override fun fetchDetails(): GroupStateDetails {
        return groupStateRef.details
    }

    override fun update(name: String, deviceStates: Map<DeviceId, DeviceStateDto?>) {
        appGroupRepository.updateGroupState(groupStateRef.appGroupRef, groupStateRef.stateId, name, deviceStates)
    }

    override fun delete() {
        appGroupRepository.deleteGroupState(groupStateRef.appGroupRef, groupStateRef.stateId)
    }
}

data class GroupStateDetails(
    val id: String,
    val name: String,
    val deviceStates: Map<DeviceId, DeviceStateDto?>
)

class GroupStateNotFoundException(stateId: String) : RuntimeException("GroupState with id $stateId not found")

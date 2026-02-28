package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.*
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set
import java.util.*

@Service
class GroupStateService(
    private val groupStateRepository: GroupStateRepository,
    private val devicesService: DevicesService,
) {
    fun createGroupState(appGroup: AppGroup, name: String, deviceStates: Map<DeviceId, DeviceStateDto?>): GroupState {
        val stateId = UUID.randomUUID().toString()
        val groupStateRef = groupStateRepository.create(appGroup.asRef(), stateId, name, deviceStates)
        return RefBasedGroupState(groupStateRef, groupStateRepository)
    }

    fun getGroupState(appGroup: AppGroup, stateId: String): GroupState {
        val groupStateRef = groupStateRepository.get(appGroup.asRef(), stateId) 
            ?: throw GroupStateNotFoundException(stateId)
        return RefBasedGroupState(groupStateRef, groupStateRepository)
    }

    fun listAllGroupStates(appGroup: AppGroup): List<GroupState> {
        return groupStateRepository.getAll(appGroup.asRef())
            .map { RefBasedGroupState(it, groupStateRepository) }
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
    private val groupStateRepository: GroupStateRepository
) : GroupState {
    override fun asRef(): GroupStateRef = groupStateRef

    override fun fetchDetails(): GroupStateDetails {
        return groupStateRepository.fetchDetails(groupStateRef)
    }

    override fun update(name: String, deviceStates: Map<DeviceId, DeviceStateDto?>) {
        groupStateRepository.update(groupStateRef, name, deviceStates)
    }

    override fun delete() {
        groupStateRepository.delete(groupStateRef)
    }
}

data class GroupStateDetails(
    val id: String,
    val name: String,
    val deviceStates: Map<DeviceId, DeviceStateDto?>
)

class GroupStateNotFoundException(stateId: String) : RuntimeException("GroupState with id $stateId not found")

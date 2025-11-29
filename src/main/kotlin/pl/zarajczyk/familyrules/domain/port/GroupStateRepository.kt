package pl.zarajczyk.familyrules.domain.port

import pl.zarajczyk.familyrules.domain.DeviceId
import java.util.*

interface GroupStateRepository {
    fun create(appGroupRef: AppGroupRef, stateId: String, name: String, deviceStates: Map<DeviceId, DeviceStateDto>): GroupStateRef
    fun get(appGroupRef: AppGroupRef, stateId: String): GroupStateRef?
    fun getAll(appGroupRef: AppGroupRef): List<GroupStateRef>
    fun fetchDetails(groupStateRef: GroupStateRef): pl.zarajczyk.familyrules.domain.GroupStateDetails
    fun update(groupStateRef: GroupStateRef, name: String, deviceStates: Map<DeviceId, DeviceStateDto>)
    fun delete(groupStateRef: GroupStateRef)
}

/**
 * Represents abstract reference to the database object related to the given group state
 */
interface GroupStateRef

package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.GroupStateDetails
import pl.zarajczyk.familyrules.domain.port.*

@Service
class FirestoreAppGroupRepository(
    private val firestore: Firestore,
    private val devicesRepository: DevicesRepository
) : AppGroupRepository {

    // App group operations
    override fun createAppGroup(userRef: UserRef, groupId: String, name: String, color: String): AppGroupRef {
        val groupData = mapOf(
            "id" to groupId,
            "name" to name,
            "color" to color,
            "members" to emptyMap<String, Any>(),
            "states" to emptyMap<String, Any>(),
        )

        val doc = (userRef as FirestoreUserRef).doc
            .collection("appGroups")
            .document(groupId)

        doc.set(groupData).get()

        return FirestoreAppGroupRef(doc)
    }

    override fun get(userRef: UserRef, groupId: String): AppGroupRef? {
        return (userRef as FirestoreUserRef).doc
            .collection("appGroups")
            .document(groupId)
            .let {
                if (it.get().get().exists())
                    FirestoreAppGroupRef(it)
                else
                    null
            }
    }

    override fun getAll(userRef: UserRef): List<AppGroupRef> {
        val snapshot = (userRef as FirestoreUserRef).doc
            .collection("appGroups")
            .get()
            .get()

        return snapshot.documents.map { FirestoreAppGroupRef(it.reference) }
    }

    override fun fetchDetails(appGroupRef: AppGroupRef): AppGroupDto {
        val snapshot = (appGroupRef as FirestoreAppGroupRef).ref.get().get()
        return snapshot.toAppGroupDocument().toDomain()
    }

    override fun rename(appGroupRef: AppGroupRef, newName: String) {
        (appGroupRef as FirestoreAppGroupRef).ref.update("name", newName).get()
    }

    override fun delete(appGroupRef: AppGroupRef) {
        (appGroupRef as FirestoreAppGroupRef).ref.delete().get()
    }

    // Members operations (stored as Firestore native map: members[deviceId] = List<appId>)

    override fun setMembers(appGroupRef: AppGroupRef, deviceRef: DeviceRef, apps: Set<AppTechnicalId>) {
        val deviceId = devicesRepository.fetchDetails(deviceRef).deviceId.toString()
        val ref = (appGroupRef as FirestoreAppGroupRef).ref
        ref.update("members.$deviceId", apps.toList()).get()
    }

    override fun getMembers(appGroupRef: AppGroupRef, deviceRef: DeviceRef): Set<AppTechnicalId> {
        val deviceId = devicesRepository.fetchDetails(deviceRef).deviceId.toString()
        val details = fetchDetails(appGroupRef)
        return details.members[deviceId] ?: emptySet()
    }

    // GroupState operations (stored as Firestore native map: states[stateId] = {name, deviceStates})

    override fun createGroupState(
        appGroupRef: AppGroupRef,
        stateId: String,
        name: String,
        deviceStates: Map<DeviceId, DeviceStateDto?>
    ): GroupStateRef {
        val ref = (appGroupRef as FirestoreAppGroupRef).ref
        val stateData = mapOf(
            "name" to name,
            "deviceStates" to encodeDeviceStatesMap(deviceStates),
        )
        ref.update("states.$stateId", stateData).get()
        return FirestoreGroupStateRef(appGroupRef, stateId)
    }

    override fun getGroupState(appGroupRef: AppGroupRef, stateId: String): GroupStateRef? {
        val details = fetchDetails(appGroupRef)
        return if (details.states.containsKey(stateId))
            FirestoreGroupStateRef(appGroupRef, stateId)
        else
            null
    }

    override fun getAllGroupStates(appGroupRef: AppGroupRef): List<GroupStateRef> {
        val details = fetchDetails(appGroupRef)
        return details.states.keys.map { FirestoreGroupStateRef(appGroupRef, it) }
    }

    override fun fetchGroupStateDetails(groupStateRef: GroupStateRef): GroupStateDetails {
        groupStateRef as FirestoreGroupStateRef
        val details = fetchDetails(groupStateRef.appGroupRef)
        return details.states[groupStateRef.stateId]
            ?: throw IllegalStateException("GroupState ${groupStateRef.stateId} not found in appGroup")
    }

    override fun updateGroupState(
        appGroupRef: AppGroupRef,
        stateId: String,
        name: String,
        deviceStates: Map<DeviceId, DeviceStateDto?>
    ) {
        val ref = (appGroupRef as FirestoreAppGroupRef).ref
        val stateData = mapOf(
            "name" to name,
            "deviceStates" to encodeDeviceStatesMap(deviceStates),
        )
        ref.update("states.$stateId", stateData).get()
    }

    override fun deleteGroupState(appGroupRef: AppGroupRef, stateId: String) {
        val ref = (appGroupRef as FirestoreAppGroupRef).ref
        ref.update("states.$stateId", com.google.cloud.firestore.FieldValue.delete()).get()
    }
}

data class FirestoreAppGroupRef(
    val ref: DocumentReference
) : AppGroupRef

data class FirestoreGroupStateRef(
    override val appGroupRef: AppGroupRef,
    override val stateId: String,
) : GroupStateRef

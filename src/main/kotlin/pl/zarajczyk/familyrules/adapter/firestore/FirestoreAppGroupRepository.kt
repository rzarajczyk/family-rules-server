package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.GroupStateDetails
import pl.zarajczyk.familyrules.domain.port.*

@Service
class FirestoreAppGroupRepository(
    private val firestore: Firestore
) : AppGroupRepository {

    // App group operations
    override fun createAppGroup(userRef: UserRef, groupId: String, name: String, description: String): AppGroupRef {
        val groupData = mapOf(
            "id" to groupId,
            "name" to name,
            "description" to description,
            "members" to emptyMap<String, Any>(),
            "states" to emptyMap<String, Any>(),
        )

        val doc = (userRef as FirestoreUserRef).doc
            .collection("appGroups")
            .document(groupId)

        doc.set(groupData).get()

        val details = AppGroupDto(id = groupId, name = name, description = description)
        return FirestoreAppGroupRef(doc, details)
    }

    override fun get(userRef: UserRef, groupId: String): AppGroupRef? {
        val docRef = (userRef as FirestoreUserRef).doc
            .collection("appGroups")
            .document(groupId)
        val snapshot = docRef.get().get()
        return if (snapshot.exists())
            FirestoreAppGroupRef(docRef, snapshot.toAppGroupDocument().toDomain())
        else
            null
    }

    override fun getAll(userRef: UserRef): List<AppGroupRef> {
        val snapshot = (userRef as FirestoreUserRef).doc
            .collection("appGroups")
            .get()
            .get()

        return snapshot.documents.map { doc ->
            FirestoreAppGroupRef(doc.reference, doc.toAppGroupDocument().toDomain())
        }
    }

    override fun rename(appGroupRef: AppGroupRef, newName: String) {
        (appGroupRef as FirestoreAppGroupRef).ref.update("name", newName).get()
    }

    override fun updateDescription(appGroupRef: AppGroupRef, newDescription: String) {
        (appGroupRef as FirestoreAppGroupRef).ref.update("description", newDescription).get()
    }

    override fun delete(appGroupRef: AppGroupRef) {
        (appGroupRef as FirestoreAppGroupRef).ref.delete().get()
    }

    // Members operations (stored as Firestore native map: members[deviceId] = List<appId>)

    override fun setMembers(appGroupRef: AppGroupRef, deviceRef: DeviceRef, apps: Set<AppTechnicalId>) {
        val deviceId = deviceRef.getDeviceId().toString()
        val ref = (appGroupRef as FirestoreAppGroupRef).ref
        ref.update("members.$deviceId", apps.toList()).get()
    }

    override fun getMembers(appGroupRef: AppGroupRef, deviceRef: DeviceRef): Set<AppTechnicalId> {
        val deviceId = deviceRef.getDeviceId().toString()
        val snapshot = (appGroupRef as FirestoreAppGroupRef).ref.get().get()
        return snapshot.toAppGroupDocument().toDomain().members[deviceId] ?: emptySet()
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
        val stateDetails = GroupStateDetails(id = stateId, name = name, deviceStates = deviceStates)
        return FirestoreGroupStateRef(appGroupRef, stateId, stateDetails)
    }

    override fun getGroupState(appGroupRef: AppGroupRef, stateId: String): GroupStateRef? {
        val dto = (appGroupRef as FirestoreAppGroupRef).details
        val stateDetails = dto.states[stateId] ?: return null
        return FirestoreGroupStateRef(appGroupRef, stateId, stateDetails)
    }

    override fun getAllGroupStates(appGroupRef: AppGroupRef): List<GroupStateRef> {
        val dto = (appGroupRef as FirestoreAppGroupRef).details
        return dto.states.map { (stateId, stateDetails) ->
            FirestoreGroupStateRef(appGroupRef, stateId, stateDetails)
        }
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
    val ref: DocumentReference,
    override val details: AppGroupDto,
) : AppGroupRef

data class FirestoreGroupStateRef(
    override val appGroupRef: AppGroupRef,
    override val stateId: String,
    override val details: GroupStateDetails,
) : GroupStateRef

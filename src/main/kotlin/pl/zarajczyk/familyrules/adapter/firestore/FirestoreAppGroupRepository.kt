package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.*

@Service
class FirestoreAppGroupRepository(
    private val firestore: Firestore,
    private val dataRepository: DataRepository
) : AppGroupRepository {
    private val json = Json { ignoreUnknownKeys = true }

    // App group operations
    override fun createAppGroup(userRef: UserRef, groupId: String, name: String, color: String): AppGroupRef {
        val groupData = mapOf(
            "id" to groupId,
            "name" to name,
            "color" to color,
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
        val data = (appGroupRef as FirestoreAppGroupRef).ref.get().get()
        return data.readAppGroupDto()
    }

    private fun DocumentSnapshot.readAppGroupDto(): AppGroupDto =
        AppGroupDto(
            id = getString("id") ?: throw RuntimeException("AppGroup id not found"),
            name = getString("name") ?: throw RuntimeException("AppGroup name not found"),
            color = getString("color") ?: throw RuntimeException("AppGroup color not found"),
        )

    override fun rename(appGroupRef: AppGroupRef, newName: String) {
        val groupRef = (appGroupRef as FirestoreAppGroupRef).ref

        // Update the group name
        groupRef.update("name", newName).get()
    }

    override fun delete(appGroupRef: AppGroupRef) {
        // First, remove all memberships for this group
        val groupId = fetchDetails(appGroupRef).id
        // TODO - to nie powinno być tutaj
        val memberships = firestore.collectionGroup("appGroupMemberships")
            .whereEqualTo("groupId", groupId)
            .get()
            .get()

        val batch = firestore.batch()
        memberships.documents.forEach { doc ->
            batch.delete(doc.reference)
        }

        // Delete the group itself
        (appGroupRef as FirestoreAppGroupRef).ref.delete().get()

        batch.commit().get()
    }

    fun getDeviceMembership(
        appGroupRef: AppGroupRef,
        deviceRef: DeviceRef
    ): DocumentReference {
        val deviceId = dataRepository.getInstance(deviceRef).id.toString()
        val ref = (appGroupRef as FirestoreAppGroupRef).ref
            .collection("members")
            .document(deviceId)
        return ref
    }

    override fun addMember(
        appGroupRef: AppGroupRef,
        deviceRef: DeviceRef,
        appTechnicalId: String
    ) {
        val (ref, currentMembers) = getMembersInternal(appGroupRef, deviceRef)
        val modifiedMembersString = json.encodeToString(currentMembers + appTechnicalId)
        ref.set(
            mapOf("apps" to modifiedMembersString)
        ).get()
    }

    override fun removeMember(
        appGroupRef: AppGroupRef,
        deviceRef: DeviceRef,
        appTechnicalId: String
    ) {
        val (ref, currentMembers) = getMembersInternal(appGroupRef, deviceRef)
        val modifiedMembersString = json.encodeToString(currentMembers - appTechnicalId)
        ref.set(
            mapOf("apps" to modifiedMembersString)
        ).get()
    }

    override fun getMembers(
        appGroupRef: AppGroupRef,
        deviceRef: DeviceRef
    ): Set<String> {
        val (_, currentMembers) = getMembersInternal(appGroupRef, deviceRef)
        return currentMembers
    }

    private fun getMembersInternal(
        appGroupRef: AppGroupRef,
        deviceRef: DeviceRef
    ): Pair<DocumentReference, Set<String>> {
        val ref = getDeviceMembership(appGroupRef, deviceRef)
        val currentMembersString = ref.get().get().getString("apps") ?: "[]"
        val currentMembers = json.decodeFromString<Set<String>>(currentMembersString)
        return Pair( ref, currentMembers)
    }



}

data class FirestoreAppGroupRef(
    val ref: DocumentReference
) : AppGroupRef
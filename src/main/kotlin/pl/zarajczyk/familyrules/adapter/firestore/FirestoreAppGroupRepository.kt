package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.AppGroupColorPalette
import pl.zarajczyk.familyrules.domain.AppGroupDto
import pl.zarajczyk.familyrules.domain.AppGroupMembershipDto
import pl.zarajczyk.familyrules.domain.AppGroupRef
import pl.zarajczyk.familyrules.domain.AppGroupRepository
import pl.zarajczyk.familyrules.domain.DataRepository
import pl.zarajczyk.familyrules.domain.InstanceId
import pl.zarajczyk.familyrules.domain.InstanceRef
import pl.zarajczyk.familyrules.domain.UserRef

@Service
class FirestoreAppGroupRepository(
    private val firestore: Firestore,
    private val dataRepository: DataRepository
): AppGroupRepository {
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
        return AppGroupDto(
            id = data.getString("id") ?: throw RuntimeException("AppGroup id not found"),
            name = data.getString("name") ?: throw RuntimeException("AppGroup name not found"),
            color = data.getString("color") ?: throw RuntimeException("AppGroup color not found"),
        )
    }

//    override fun getAppGroups(username: String): List<AppGroupDto> {
//        val groups = firestore.collection("users")
//            .document(username)
//            .collection("appGroups")
//            .get()
//            .get()
//
//        return groups.documents.map { doc ->
//            AppGroupDto(
//                id = doc.getString("id") ?: "",
//                name = doc.getString("name") ?: "",
//                color = doc.getString("color") ?: AppGroupColorPalette.getDefaultColor(),
//            )
//        }.sortedBy { it.name }
//    }

    override fun renameAppGroup(username: String, groupId: String, newName: String): AppGroupDto {
        val groupRef = firestore.collection("users")
            .document(username)
            .collection("appGroups")
            .document(groupId)

        // Update the group name
        groupRef.update("name", newName).get()

        // Get the updated group data
        val updatedGroup = groupRef.get().get()
        return AppGroupDto(
            id = updatedGroup.getString("id") ?: groupId,
            name = updatedGroup.getString("name") ?: newName,
            color = updatedGroup.getString("color") ?: AppGroupColorPalette.getDefaultColor(),
        )
    }

    override fun deleteAppGroup(username: String, groupId: String) {
        // First, remove all memberships for this group
        val memberships = firestore.collectionGroup("appGroupMemberships")
            .whereEqualTo("groupId", groupId)
            .get()
            .get()

        val batch = firestore.batch()
        memberships.documents.forEach { doc ->
            batch.delete(doc.reference)
        }

        // Delete the group itself
        firestore.collection("users")
            .document(username)
            .collection("appGroups")
            .document(groupId)
            .delete()
            .get()

        batch.commit().get()
    }

    override fun addAppToGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String) {
        val membershipId = "${instanceId}_${appPath}_${groupId}".hashCode().toString()

        val membershipData = mapOf(
            "appPath" to appPath,
            "groupId" to groupId,
            "instanceId" to instanceId.toString(),
            "username" to username
        )

        firestore.collection("users")
            .document(username)
            .collection("instances")
            .document(instanceId.toString())
            .collection("appGroupMemberships")
            .document(membershipId)
            .set(membershipData)
            .get()
    }

    override fun removeAppFromGroup(username: String, instanceId: InstanceId, appPath: String, groupId: String) {
        val membershipId = "${instanceId}_${appPath}_${groupId}".hashCode().toString()

        firestore.collection("users")
            .document(username)
            .collection("instances")
            .document(instanceId.toString())
            .collection("appGroupMemberships")
            .document(membershipId)
            .delete()
            .get()
    }

    override fun getAppGroupMemberships(instance: InstanceRef): List<AppGroupMembershipDto> {
        val instanceDoc = (instance as FirestoreInstanceRef).document
        val memberships = instanceDoc.reference
            .collection("appGroupMemberships")
            .get()
            .get()

        val instanceBasicData = dataRepository.getInstanceBasicData(instance)

        return memberships.documents.map { doc ->
            AppGroupMembershipDto(
                appPath = doc.getString("appPath") ?: "",
                groupId = doc.getString("groupId") ?: "",
                instance = instanceBasicData
            )
        }
    }

    override fun getAppGroupMemberships(username: String): List<AppGroupMembershipDto> {
        val instances = dataRepository.findInstances(username)
        return instances.flatMap { getAppGroupMemberships(instance = it) }
    }
}

data class FirestoreAppGroupRef(
    val ref: DocumentReference
) : AppGroupRef
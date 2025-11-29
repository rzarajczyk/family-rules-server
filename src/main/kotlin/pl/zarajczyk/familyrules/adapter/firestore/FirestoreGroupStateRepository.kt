package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.GroupStateDetails
import pl.zarajczyk.familyrules.domain.port.*
import java.util.*

@Service
class FirestoreGroupStateRepository : GroupStateRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun create(
        appGroupRef: AppGroupRef,
        stateId: String,
        name: String,
        deviceStates: Map<DeviceId, DeviceStateDto>
    ): GroupStateRef {
        val stateData = mapOf(
            "id" to stateId,
            "name" to name,
            "deviceStates" to encodeDeviceStatesMap(deviceStates)
        )

        val doc = (appGroupRef as FirestoreAppGroupRef).ref
            .collection("groupStates")
            .document(stateId)

        doc.set(stateData).get()

        return FirestoreGroupStateRef(doc)
    }

    override fun get(appGroupRef: AppGroupRef, stateId: String): GroupStateRef? {
        return (appGroupRef as FirestoreAppGroupRef).ref
            .collection("groupStates")
            .document(stateId)
            .let {
                if (it.get().get().exists())
                    FirestoreGroupStateRef(it)
                else
                    null
            }
    }

    override fun getAll(appGroupRef: AppGroupRef): List<GroupStateRef> {
        val snapshot = (appGroupRef as FirestoreAppGroupRef).ref
            .collection("groupStates")
            .get()
            .get()

        return snapshot.documents.map { FirestoreGroupStateRef(it.reference) }
    }

    override fun fetchDetails(groupStateRef: GroupStateRef): GroupStateDetails {
        val data = (groupStateRef as FirestoreGroupStateRef).ref.get().get()
        return GroupStateDetails(
            id = data.getStringOrThrow("id"),
            name = data.getStringOrThrow("name"),
            deviceStates = decodeDeviceStatesMap(data.getString("deviceStates") ?: "{}")
        )
    }

    override fun update(
        groupStateRef: GroupStateRef,
        name: String,
        deviceStates: Map<DeviceId, DeviceStateDto>
    ) {
        val updateData = mapOf(
            "name" to name,
            "deviceStates" to encodeDeviceStatesMap(deviceStates)
        )
        (groupStateRef as FirestoreGroupStateRef).ref.update(updateData).get()
    }

    override fun delete(groupStateRef: GroupStateRef) {
        (groupStateRef as FirestoreGroupStateRef).ref.delete().get()
    }

    private fun encodeDeviceStatesMap(deviceStates: Map<DeviceId, DeviceStateDto>): String {
        val stringMap = deviceStates.mapKeys { it.key.toString() }
        return json.encodeToString(stringMap)
    }

    private fun decodeDeviceStatesMap(encoded: String): Map<DeviceId, DeviceStateDto> {
        val stringMap = json.decodeFromString<Map<String, DeviceStateDto>>(encoded)
        return stringMap.mapKeys { UUID.fromString(it.key) }
    }
}

data class FirestoreGroupStateRef(
    val ref: DocumentReference
) : GroupStateRef

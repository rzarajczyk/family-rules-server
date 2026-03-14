package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.firestore.DocumentSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.GroupStateDetails
import pl.zarajczyk.familyrules.domain.port.*
import java.util.UUID

// ---------------------------------------------------------------------------
// Wire DTOs — mirror the Firestore document structure exactly
// ---------------------------------------------------------------------------

@Serializable
data class FirestoreAppGroupDocument(
    val id: String,
    val name: String,
    val description: String = "",
    /** members[deviceId] = list of app technical IDs */
    val members: Map<String, List<String>> = emptyMap(),
    /** states[stateId] = group state document */
    val states: Map<String, FirestoreGroupStateDocument> = emptyMap(),
)

@Serializable
data class FirestoreGroupStateDocument(
    val name: String,
    val deviceStates: Map<String, FirestoreDeviceStateDocument?> = emptyMap(),
)

@Serializable
data class FirestoreDeviceStateDocument(
    val deviceState: String,
    val extra: String? = null,
)

// ---------------------------------------------------------------------------
// DocumentSnapshot → FirestoreAppGroupDocument
// ---------------------------------------------------------------------------

private val json = Json { ignoreUnknownKeys = true }

/**
 * Decodes a Firestore [DocumentSnapshot] into a typed [FirestoreAppGroupDocument]
 * without any raw unchecked casts. The round-trip goes through kotlinx.serialization
 * JSON so the compiler can verify every field access.
 */
fun DocumentSnapshot.toAppGroupDocument(): FirestoreAppGroupDocument {
    val raw: Map<String, Any?> = data ?: emptyMap()
    val jsonElement = raw.toJsonElement()
    return json.decodeFromJsonElement(jsonElement)
}

// ---------------------------------------------------------------------------
// FirestoreAppGroupDocument → domain types
// ---------------------------------------------------------------------------

fun FirestoreAppGroupDocument.toDomain(): AppGroupDto = AppGroupDto(
    id = id,
    name = name,
    description = description,
    members = members.mapValues { (_, apps) -> apps.toSet() },
    states = states.mapNotNull { (stateId, stateDoc) ->
        val deviceStates: Map<DeviceId, DeviceStateDto?> = stateDoc.deviceStates.mapNotNull { (deviceIdStr, dsDoc) ->
            val deviceId = runCatching { UUID.fromString(deviceIdStr) }.getOrNull() ?: return@mapNotNull null
            val dto = dsDoc?.let { DeviceStateDto(deviceState = it.deviceState, extra = it.extra) }
            deviceId to dto
        }.toMap()
        stateId to GroupStateDetails(id = stateId, name = stateDoc.name, deviceStates = deviceStates)
    }.toMap(),
)

// ---------------------------------------------------------------------------
// Domain types → Firestore map (for writes)
// ---------------------------------------------------------------------------

fun encodeDeviceStatesMap(deviceStates: Map<DeviceId, DeviceStateDto?>): Map<String, Any?> =
    deviceStates.mapKeys { it.key.toString() }.mapValues { (_, dto) ->
        if (dto == null) null
        else mapOf("deviceState" to dto.deviceState, "extra" to dto.extra)
    }

// ---------------------------------------------------------------------------
// Internal helper: Map<String, Any?> (Firestore SDK output) → JsonElement
// ---------------------------------------------------------------------------

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(
        this.entries.associate { (k, v) -> k.toString() to v.toJsonElement() }
    )
    is List<*> -> JsonArray(this.map { it.toJsonElement() })
    else -> JsonPrimitive(this.toString())
}

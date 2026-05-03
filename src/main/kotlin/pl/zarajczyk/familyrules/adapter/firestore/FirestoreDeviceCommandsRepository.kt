package pl.zarajczyk.familyrules.adapter.firestore

import com.google.cloud.Timestamp
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import kotlinx.datetime.Instant
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.CommandLifecycleStatus
import pl.zarajczyk.familyrules.domain.port.CommandAckDto
import pl.zarajczyk.familyrules.domain.port.CommandResultDto
import pl.zarajczyk.familyrules.domain.port.DeviceCommandDto
import pl.zarajczyk.familyrules.domain.port.DeviceCommandsRepository
import pl.zarajczyk.familyrules.domain.port.DeviceRef
import java.util.UUID

@Service
class FirestoreDeviceCommandsRepository(
    private val firestore: Firestore,
) : DeviceCommandsRepository {

    override fun create(device: DeviceRef, command: DeviceCommandDto) {
        commandDocument(device, command.commandId).set(command.toDocumentData()).get()
    }

    override fun get(device: DeviceRef, commandId: String): DeviceCommandDto? {
        val doc = commandDocument(device, commandId).get().get()
        if (!doc.exists()) return null
        return doc.toCommandDto()
    }

    override fun getPending(device: DeviceRef): List<DeviceCommandDto> {
        return commandsCollection(device)
            .get()
            .get()
            .documents
            .map { it.toCommandDto() }
            .filter { it.status == CommandLifecycleStatus.QUEUED }
            .sortedBy { it.createdAt }
    }

    override fun markDelivered(device: DeviceRef, commandIds: Collection<String>, deliveredAt: Instant) {
        if (commandIds.isEmpty()) return
        val batch = firestore.batch()
        commandIds.forEach { commandId ->
            val doc = commandDocument(device, commandId)
            batch.update(doc, mapOf(
                "lastDeliveredAt" to deliveredAt.toTimestamp(),
                "deliveryAttempts" to FieldValue.increment(1),
            ))
        }
        batch.commit().get()
    }

    override fun acknowledge(device: DeviceRef, acknowledgements: List<CommandAckDto>) {
        if (acknowledgements.isEmpty()) return
        val batch = firestore.batch()
        acknowledgements.forEach { ack ->
            val doc = commandDocument(device, ack.commandId)
            val snapshot = doc.get().get()
            if (!snapshot.exists()) return@forEach
            val status = snapshot.getStringOrThrow("status")
            if (status == CommandLifecycleStatus.COMPLETED.name || status == CommandLifecycleStatus.FAILED.name) {
                return@forEach
            }
            batch.update(doc, mapOf(
                "status" to CommandLifecycleStatus.ACKNOWLEDGED.name,
                "acknowledgedAt" to ack.receivedAt.toTimestamp(),
            ))
        }
        batch.commit().get()
    }

    override fun storeResults(device: DeviceRef, results: List<CommandResultDto>) {
        if (results.isEmpty()) return
        val batch = firestore.batch()
        results.forEach { result ->
            val doc = commandDocument(device, result.commandId)
            val snapshot = doc.get().get()
            if (!snapshot.exists()) return@forEach

            val existingPayload = snapshot.getString("responsePayloadJson")
            val existingType = snapshot.getString("responseType")
            val completedAt = snapshot.getTimestamp("completedAt")
            if (completedAt != null) {
                if (existingPayload == result.responsePayloadJson && existingType == result.responseType) {
                    return@forEach
                }
                throw ConflictingCommandResultException(result.commandId)
            }

            val lifecycleStatus = if (result.status == pl.zarajczyk.familyrules.domain.CommandResultStatus.SUCCEEDED) {
                CommandLifecycleStatus.COMPLETED
            } else {
                CommandLifecycleStatus.FAILED
            }

            batch.update(doc, mapOf(
                "status" to lifecycleStatus.name,
                "acknowledgedAt" to (snapshot.getTimestamp("acknowledgedAt") ?: result.completedAt.toTimestamp()),
                "completedAt" to result.completedAt.toTimestamp(),
                "resultStatus" to result.status.name,
                "responseType" to result.responseType,
                "responsePayloadJson" to result.responsePayloadJson,
            ))
        }
        batch.commit().get()
    }

    override fun hasPending(device: DeviceRef): Boolean {
        return getPending(device).isNotEmpty()
    }

    private fun commandsCollection(device: DeviceRef) =
        (device as FirestoreDeviceRef).document.reference.collection("serverCommands")

    private fun commandDocument(device: DeviceRef, commandId: String): DocumentReference =
        commandsCollection(device).document(commandId)

    private fun DeviceCommandDto.toDocumentData() = mapOf(
        "commandId" to commandId,
        "deviceId" to deviceId.toString(),
        "commandName" to commandName,
        "status" to status.name,
        "protocolVersion" to protocolVersion.toLong(),
        "createdAt" to createdAt.toTimestamp(),
        "lastDeliveredAt" to lastDeliveredAt?.toTimestamp(),
        "acknowledgedAt" to acknowledgedAt?.toTimestamp(),
        "completedAt" to completedAt?.toTimestamp(),
        "deliveryAttempts" to deliveryAttempts.toLong(),
        "resultStatus" to resultStatus?.name,
        "responseType" to responseType,
        "responsePayloadJson" to responsePayloadJson,
    )

    private fun com.google.cloud.firestore.DocumentSnapshot.toCommandDto() = DeviceCommandDto(
        commandId = getStringOrThrow("commandId"),
        deviceId = UUID.fromString(getStringOrThrow("deviceId")),
        commandName = getStringOrThrow("commandName"),
        status = CommandLifecycleStatus.valueOf(getStringOrThrow("status")),
        protocolVersion = getLongOrThrow("protocolVersion").toInt(),
        createdAt = getTimestamp("createdAt")!!.toInstant(),
        lastDeliveredAt = getTimestamp("lastDeliveredAt")?.toInstant(),
        acknowledgedAt = getTimestamp("acknowledgedAt")?.toInstant(),
        completedAt = getTimestamp("completedAt")?.toInstant(),
        deliveryAttempts = getLong("deliveryAttempts")?.toInt() ?: 0,
        resultStatus = getString("resultStatus")?.let { pl.zarajczyk.familyrules.domain.CommandResultStatus.valueOf(it) },
        responseType = getString("responseType"),
        responsePayloadJson = getString("responsePayloadJson"),
    )

    private fun Instant.toTimestamp() = Timestamp.ofTimeSecondsAndNanos(epochSeconds, nanosecondsOfSecond)
    private fun Timestamp.toInstant() = Instant.fromEpochSeconds(seconds, nanos)
}

class ConflictingCommandResultException(commandId: String) : RuntimeException("Conflicting result for command $commandId")

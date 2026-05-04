package pl.zarajczyk.familyrules.domain

interface SendLogsResultStorage {
    fun store(deviceId: DeviceId, commandId: String, rawLogsText: String, truncated: Boolean, collectedAt: String): StoredSendLogsPayload
    fun read(objectName: String): String
}

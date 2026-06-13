package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto
import pl.zarajczyk.familyrules.domain.port.ForceReportPushResult
import pl.zarajczyk.familyrules.domain.port.ForceReportPushSender
import pl.zarajczyk.familyrules.domain.port.ForceReportPushStatus
import pl.zarajczyk.familyrules.domain.port.ValueUpdate.Companion.set

@Service
class ForceReportPushService(
    senders: List<ForceReportPushSender>,
) {
    private val sendersByCapability = senders.associateBy { it.supportedCapability() }

    fun send(device: Device): ForceReportPushResult {
        val capability = deviceForceReportPushCapability(device.getDetails().capabilities)
            ?: return ForceReportPushResult(ForceReportPushStatus.NOT_CAPABLE)

        val pushToken = device.getDetails().pushToken?.trim().orEmpty()
        if (pushToken.isEmpty()) {
            return ForceReportPushResult(ForceReportPushStatus.NO_TOKEN)
        }

        val sender = sendersByCapability[capability]
            ?: return ForceReportPushResult(
                status = ForceReportPushStatus.UNSUPPORTED_CAPABILITY,
                message = "No push sender registered for $capability",
            )

        val result = sender.sendForceReport(pushToken)
        if (result.clearStoredToken) {
            device.update(
                DeviceDetailsUpdateDto(
                    pushToken = set(null),
                    pushTokenUpdatedAt = set(null),
                )
            )
        }
        return result
    }
}

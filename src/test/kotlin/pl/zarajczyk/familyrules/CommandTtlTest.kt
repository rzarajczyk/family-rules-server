package pl.zarajczyk.familyrules

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import pl.zarajczyk.familyrules.domain.CommandLifecycleStatus
import pl.zarajczyk.familyrules.domain.commandDeliveryTtl
import pl.zarajczyk.familyrules.domain.isCommandUndeliveredDeliveryExpired
import pl.zarajczyk.familyrules.domain.port.DeviceCommandDto
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class CommandTtlTest : FunSpec({

    test("commandDeliveryTtl returns configured durations") {
        commandDeliveryTtl("SEND_LOGS") shouldBe 6.hours
        commandDeliveryTtl("DISABLE") shouldBe 10.minutes
        commandDeliveryTtl("UNINSTALL") shouldBe 10.minutes
        commandDeliveryTtl("UNKNOWN") shouldBe null
    }

    test("isCommandUndeliveredDeliveryExpired is false before TTL elapses") {
        val createdAt = Instant.fromEpochSeconds(1_000_000)
        val disable = queuedCommand(createdAt = createdAt, commandName = "DISABLE")
        val sendLogs = queuedCommand(createdAt = createdAt, commandName = "SEND_LOGS")

        isCommandUndeliveredDeliveryExpired(disable, createdAt + 9.minutes) shouldBe false
        isCommandUndeliveredDeliveryExpired(sendLogs, createdAt + 6.hours - 1.minutes) shouldBe false
    }

    test("isCommandUndeliveredDeliveryExpired is true after TTL elapses for undelivered queued commands") {
        val createdAt = Instant.fromEpochSeconds(1_000_000)
        val disable = queuedCommand(createdAt = createdAt, commandName = "DISABLE")
        val sendLogs = queuedCommand(createdAt = createdAt, commandName = "SEND_LOGS")

        isCommandUndeliveredDeliveryExpired(disable, createdAt + 10.minutes) shouldBe true
        isCommandUndeliveredDeliveryExpired(sendLogs, createdAt + 6.hours) shouldBe true
        isCommandUndeliveredDeliveryExpired(sendLogs, createdAt + (6.hours - 1.minutes)) shouldBe false
    }

    test("isCommandUndeliveredDeliveryExpired ignores delivered, acknowledged, and completed commands") {
        val createdAt = Instant.fromEpochSeconds(1_000_000)
        val expiredAt = createdAt + 1.hours

        isCommandUndeliveredDeliveryExpired(
            queuedCommand(createdAt = createdAt, commandName = "DISABLE").copy(lastDeliveredAt = createdAt),
            expiredAt,
        ) shouldBe false

        isCommandUndeliveredDeliveryExpired(
            queuedCommand(createdAt = createdAt, commandName = "DISABLE").copy(status = CommandLifecycleStatus.ACKNOWLEDGED),
            expiredAt,
        ) shouldBe false

        isCommandUndeliveredDeliveryExpired(
            queuedCommand(createdAt = createdAt, commandName = "DISABLE").copy(status = CommandLifecycleStatus.COMPLETED),
            expiredAt,
        ) shouldBe false
    }
})

private fun queuedCommand(createdAt: Instant, commandName: String): DeviceCommandDto =
    DeviceCommandDto(
        commandId = UUID.randomUUID().toString(),
        deviceId = UUID.randomUUID(),
        commandName = commandName,
        status = CommandLifecycleStatus.QUEUED,
        protocolVersion = 1,
        createdAt = createdAt,
        lastDeliveredAt = null,
        acknowledgedAt = null,
        completedAt = null,
        deliveryAttempts = 0,
        resultStatus = null,
        responseType = null,
        responsePayloadJson = null,
    )

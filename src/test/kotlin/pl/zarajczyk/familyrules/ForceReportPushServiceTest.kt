package pl.zarajczyk.familyrules

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import pl.zarajczyk.familyrules.domain.Capability
import pl.zarajczyk.familyrules.domain.Device
import pl.zarajczyk.familyrules.domain.ForceReportPushService
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsDto
import pl.zarajczyk.familyrules.domain.port.ForceReportPushResult
import pl.zarajczyk.familyrules.domain.port.ForceReportPushSender
import pl.zarajczyk.familyrules.domain.port.ForceReportPushStatus
import java.util.UUID

class ForceReportPushServiceTest : FunSpec({

    test("send routes to matching sender and clears stale token when requested") {
        val deviceId = UUID.randomUUID()
        val device = mockk<Device>(relaxed = true)
        every { device.getDetails() } returns DeviceDetailsDto(
            deviceId = deviceId,
            deviceName = "phone",
            forcedDeviceState = null,
            clientType = "ANDROID",
            clientVersion = "v1",
            clientTimezoneOffsetSeconds = 0,
            iconData = null,
            iconType = null,
            reportIntervalSeconds = 60,
            knownApps = emptyMap(),
            availableDeviceStates = emptyList(),
            capabilities = listOf(Capability.FCM_FORCE_REPORT_PUSH),
            pushToken = "token-1",
        )

        val sender = mockk<ForceReportPushSender>()
        every { sender.supportedCapability() } returns Capability.FCM_FORCE_REPORT_PUSH
        every { sender.sendForceReport("token-1") } returns ForceReportPushResult(
            status = ForceReportPushStatus.PROVIDER_ERROR,
            clearStoredToken = true,
        )

        val service = ForceReportPushService(listOf(sender))
        val result = service.send(device)

        result.status shouldBe ForceReportPushStatus.PROVIDER_ERROR
        verify {
            device.update(
                match { update ->
                    update.pushToken.ifPresent { it } == null &&
                        update.pushTokenUpdatedAt.ifPresent { it } == null
                }
            )
        }
    }

    test("send returns NO_TOKEN when push token missing") {
        val device = mockk<Device>()
        every { device.getDetails() } returns DeviceDetailsDto(
            deviceId = UUID.randomUUID(),
            deviceName = "phone",
            forcedDeviceState = null,
            clientType = "ANDROID",
            clientVersion = "v1",
            clientTimezoneOffsetSeconds = 0,
            iconData = null,
            iconType = null,
            reportIntervalSeconds = 60,
            knownApps = emptyMap(),
            availableDeviceStates = emptyList(),
            capabilities = listOf(Capability.FCM_FORCE_REPORT_PUSH),
            pushToken = null,
        )

        val service = ForceReportPushService(emptyList())
        service.send(device).status shouldBe ForceReportPushStatus.NO_TOKEN
    }
})

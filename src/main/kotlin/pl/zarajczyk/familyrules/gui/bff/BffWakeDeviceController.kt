package pl.zarajczyk.familyrules.gui.bff

import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import pl.zarajczyk.familyrules.domain.DeviceId
import pl.zarajczyk.familyrules.domain.DevicesService
import pl.zarajczyk.familyrules.domain.ForceReportPushService
import pl.zarajczyk.familyrules.domain.UsersService
import pl.zarajczyk.familyrules.domain.port.ForceReportPushStatus

@RestController
class BffWakeDeviceController(
    private val devicesService: DevicesService,
    private val usersService: UsersService,
    private val forceReportPushService: ForceReportPushService,
) {

    @PostMapping("/bff/wake-device")
    fun wakeDevice(
        @RequestParam("instanceId") deviceId: DeviceId,
        authentication: Authentication,
    ): WakeDeviceResponse {
        val user = usersService.get(authentication.name)
        val device = devicesService.get(deviceId)
        if (device.getOwner().getDetails().username != user.getDetails().username) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }

        val result = forceReportPushService.send(device)
        if (result.status == ForceReportPushStatus.SUCCESS) {
            return WakeDeviceResponse(status = result.status.name, message = null)
        }

        val message = result.message ?: defaultMessage(result.status)
        throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message)
    }

    private fun defaultMessage(status: ForceReportPushStatus): String = when (status) {
        ForceReportPushStatus.NOT_CAPABLE -> "Device does not support force-report push"
        ForceReportPushStatus.NO_TOKEN -> "Device has not registered a push token yet"
        ForceReportPushStatus.UNSUPPORTED_CAPABILITY,
        ForceReportPushStatus.NOT_CONFIGURED -> "Force-report push is not configured on the server"
        ForceReportPushStatus.PROVIDER_ERROR -> "Push provider rejected the request"
        ForceReportPushStatus.SUCCESS -> ""
    }
}

data class WakeDeviceResponse(
    val status: String,
    val message: String?,
)

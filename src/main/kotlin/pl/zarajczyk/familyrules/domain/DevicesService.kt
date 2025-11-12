package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsDto
import pl.zarajczyk.familyrules.domain.port.DeviceRef
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import java.util.UUID

@Service
class DevicesService(
    private val devicesRepository: DevicesRepository,
    private val usersRepository: UsersRepository
) {

    fun <T> withDeviceContext(deviceId: DeviceId, action: (user: Device) -> T): T {
        val ref = devicesRepository.get(deviceId) ?: throw DeviceNotFoundException(deviceId)
        val device = RefBasedDevice(ref, devicesRepository)
        return action(device)
    }

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    fun setupNewDevice(username: String, deviceName: String, clientType: String): NewDeviceDetails {
        if (deviceName.length < 3) {
            throw IllegalInstanceName(deviceName)
        }

        val userRef = usersRepository.get(username) ?: throw UserNotFoundException(username)

        if (devicesRepository.getByName(userRef, deviceName) != null)
            throw InstanceAlreadyExists(deviceName)

        val deviceId = UUID.randomUUID()
        val token = UUID.randomUUID().toString()
        val details = DeviceDetailsDto(
            deviceId = deviceId,
            deviceName = deviceName,
            hashedToken = token.sha256(),
            clientType = clientType,
            clientVersion = "v0",
            clientTimezoneOffsetSeconds = 0L,
            deleted = false
        )

        devicesRepository.createDevice(userRef, details)

        return NewDeviceDetails(
            deviceId = deviceId,
            token = token
        )
    }
}

class IllegalInstanceName(val instanceName: String) : RuntimeException("Instance $instanceName already exists")
class InstanceAlreadyExists(val instanceName: String) : RuntimeException("Instance $instanceName has incorrect name")

data class NewDeviceDetails(
    val deviceId: DeviceId,
    val token: String
)

interface Device {
    fun validateToken(token: String): Boolean

}

data class RefBasedDevice(
    val deviceRef: DeviceRef,
    private val devicesRepository: DevicesRepository
) : Device {
    override fun validateToken(token: String): Boolean {
        return devicesRepository.fetchDetails(deviceRef, includePasswordHash = true).hashedToken == token.sha256()
    }

}
package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsDto
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import java.util.UUID

@Service
class DevicesService(
    private val devicesRepository: DevicesRepository,
    private val usersRepository: UsersRepository
) {

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    fun setupNewDevice(username: String, deviceName: String, clientType: String): NewDeviceDetails {
        if (deviceName.length < 3) {
            throw IllegalInstanceName(deviceName)
        }

        val userRef = usersRepository.get(username) ?: throw UserNotFoundException(username)

        if (devicesRepository.getByName(userRef, deviceName) != null)
            throw InstanceAlreadyExists(deviceName)

        val token = UUID.randomUUID().toString()
        val details = DeviceDetailsDto(
            deviceId = UUID.randomUUID(),
            deviceName = deviceName,
            hashedToken = token.sha256(),
            clientType = clientType,
            clientVersion = "v0",
            clientTimezoneOffsetSeconds = 0L,
            deleted = false
        )

        devicesRepository.createNewDevice(userRef, details)

        return NewDeviceDetails(
            deviceId = details.deviceId,
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
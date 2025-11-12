package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.UsersRepository

@Service
class DevicesService(
    private val devicesRepository: DevicesRepository,
    private val usersRepository: UsersRepository
) {

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    fun setupNewDevice(username: String, deviceName: String, clientType: String): NewDeviceDto {
        if (deviceName.length < 3) {
            throw IllegalInstanceName(deviceName)
        }

        val userRef = usersRepository.get(username) ?: throw RuntimeException("User $usersRepository not found")

        if (devicesRepository.getByName(userRef, deviceName) != null)
            throw InstanceAlreadyExists(deviceName)

        return devicesRepository.createNewDevice(userRef, deviceName, clientType)
    }
}
package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsDto
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto
import pl.zarajczyk.familyrules.domain.port.DeviceRef
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import pl.zarajczyk.familyrules.domain.port.UserRef
import pl.zarajczyk.familyrules.domain.port.UsersRepository
import java.util.UUID

@Service
class DevicesService(
    private val devicesRepository: DevicesRepository,
    private val usersService: UsersService
) {

    fun <T> withDeviceContext(deviceId: DeviceId, action: (user: Device) -> T): T {
        val ref = devicesRepository.get(deviceId) ?: throw DeviceNotFoundException(deviceId)
        val device = RefBasedDevice(ref, devicesRepository, usersService)
        return action(device)
    }

    fun getAllDevices(user: User): List<Device> {
        return devicesRepository
            .getAll(user.asRef())
            .map { RefBasedDevice(it, devicesRepository, usersService) }
    }

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    fun setupNewDevice(username: String, deviceName: String, clientType: String): NewDeviceDetails {
        if (deviceName.length < 3) {
            throw IllegalInstanceName(deviceName)
        }

        val user = usersService.get(username)
        val userRef = user.asRef()
        if (devicesRepository.getByName(userRef, deviceName) != null)
            throw InstanceAlreadyExists(deviceName)

        val deviceId = UUID.randomUUID()
        val token = UUID.randomUUID().toString()
        val details = DeviceDetailsDto(
            deviceId = deviceId,
            deviceName = deviceName,
            forcedDeviceState = null,
            hashedToken = token.sha256(),
            clientType = clientType,
            clientVersion = "v0",
            clientTimezoneOffsetSeconds = 0L,
            iconData = null,
            iconType = null,
            reportIntervalSeconds = 60,
            knownApps = emptyMap(),
            schedule = WeeklyScheduleDto.empty()
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

    fun asRef(): DeviceRef

    fun validateToken(token: String): Boolean

    fun update(update: DeviceDetailsUpdateDto)

    fun delete()

    fun getOwner(): User

    fun get(): DeviceDetailsDto
}

data class RefBasedDevice(
    val deviceRef: DeviceRef,
    private val devicesRepository: DevicesRepository,
    private val usersService: UsersService
) : Device {

    override fun asRef(): DeviceRef {
        return deviceRef
    }

    override fun validateToken(token: String): Boolean {
        return devicesRepository.fetchDetails(deviceRef, includePasswordHash = true).hashedToken == token.sha256()
    }

    override fun update(update: DeviceDetailsUpdateDto) {
        devicesRepository.update(deviceRef, update)
    }

    override fun delete() {
        devicesRepository.delete(deviceRef)
    }

    override fun getOwner(): User {
        return usersService.get(devicesRepository.getOwner(deviceRef))
    }


    override fun get(): DeviceDetailsDto {
        return devicesRepository.fetchDetails(deviceRef)
    }
}
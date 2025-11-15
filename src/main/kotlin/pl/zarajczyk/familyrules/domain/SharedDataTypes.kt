package pl.zarajczyk.familyrules.domain

import java.util.*

typealias DeviceId = UUID

enum class AccessLevel {
    ADMIN,
    PARENT
}

class UserNotFoundException(username: String) : RuntimeException("User $username not found")
class AppGroupNotFoundException(groupId: String) : RuntimeException("AppGroup with id $groupId not found")
class DeviceNotFoundException(deviceId: DeviceId) : RuntimeException("Device with id $deviceId not found")

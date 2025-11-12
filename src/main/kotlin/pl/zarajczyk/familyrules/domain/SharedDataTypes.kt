package pl.zarajczyk.familyrules.domain

import java.util.UUID

typealias InstanceId = UUID
typealias DeviceId = InstanceId

enum class AccessLevel {
    ADMIN,
    PARENT
}

class UserNotFoundException(username: String) : RuntimeException("User $username not found")
class AppGroupNotFoundException(groupId: String) : RuntimeException("AppGroup with id $groupId not found")

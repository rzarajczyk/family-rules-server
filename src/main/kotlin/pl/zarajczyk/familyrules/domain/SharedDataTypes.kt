package pl.zarajczyk.familyrules.domain

import java.util.UUID

typealias InstanceId = UUID
typealias DeviceId = InstanceId

enum class AccessLevel {
    ADMIN,
    PARENT
}
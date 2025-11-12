package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.DeviceStateArgument.APP_GROUP
import pl.zarajczyk.familyrules.domain.port.AppGroupDto

@Service
class DeviceStateService() {
    fun createActualInstances(deviceState: DeviceStateTypeDto, appGroups: Collection<AppGroupDetails>): List<DeviceStateInstance> {
        if (deviceState.arguments.isEmpty()) {
            return DeviceStateInstance(
                deviceState = deviceState.deviceState,
                title = deviceState.title,
                icon = deviceState.icon,
                description = deviceState.description,
                extra = null
            ).let { listOf(it) }
        }
        if (deviceState.arguments == setOf(APP_GROUP)) {
            return appGroups.map { appGroup ->
                DeviceStateInstance(
                    deviceState = deviceState.deviceState,
                    title = "${deviceState.title} (${appGroup.name})",
                    icon = deviceState.icon,
                    description = "${deviceState.description} (${appGroup.name})",
                    extra = appGroup.id
                )
            }
        }
        throw RuntimeException("Unsupported deviceState arguments: $deviceState")
    }

}

data class DeviceStateInstance(
    val deviceState: String,
    val title: String,
    val icon: String?,
    val description: String?,
    val extra: String?
)
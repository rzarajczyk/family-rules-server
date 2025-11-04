package pl.zarajczyk.familyrules.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.DeviceStateArgument.APP_GROUP

@Service
class DeviceStateService() {
    private val json = Json { ignoreUnknownKeys = true }

    fun explode(deviceState: DescriptiveDeviceStateDto, appGroups: List<AppGroupDto>): List<ExplodedDeviceState> {
        if (deviceState.arguments.isEmpty()) {
            return ExplodedDeviceState(
                deviceState = deviceState.deviceState,
                title = deviceState.title,
                icon = deviceState.icon,
                description = deviceState.description,
                extra = ""
            ).let { listOf(it) }
        }
        if (deviceState.arguments == setOf(APP_GROUP)) {
            return appGroups.map { appGroup ->
                ExplodedDeviceState(
                    deviceState = deviceState.deviceState,
                    title = "${deviceState.title} (${appGroup.name})",
                    icon = deviceState.icon,
                    description = "${deviceState.description} (${appGroup.name})",
                    extra = json.encodeToString(mapOf(APP_GROUP.name to appGroup.id))
                )
            }
        }
        throw RuntimeException("Unsupported deviceState arguments: $deviceState")
    }

}

data class ExplodedDeviceState(
    val deviceState: DeviceState,
    val title: String,
    val icon: String?,
    val description: String?,
    val extra: String
)
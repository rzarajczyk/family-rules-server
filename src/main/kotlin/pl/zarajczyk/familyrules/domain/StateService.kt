package pl.zarajczyk.familyrules.domain

import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsDto
import pl.zarajczyk.familyrules.domain.port.DeviceStateDto

@Service
class StateService {

    fun calculateCurrentDeviceState(device: Device): CurrentDeviceState {
        val deviceDetails = device.fetchDetails()
        return calculateCurrentDeviceState(deviceDetails)
    }

    fun calculateCurrentDeviceState(deviceDetails: DeviceDetailsDto): CurrentDeviceState {
        val automaticState = DeviceStateDto.default()
        val finalState = deviceDetails.forcedDeviceState ?: automaticState
        return CurrentDeviceState(
            forcedState = deviceDetails.forcedDeviceState,
            automaticState = automaticState,
            finalState = finalState
        )
    }

}

data class CurrentDeviceState(
    val finalState: DeviceStateDto,
    val automaticState: DeviceStateDto,
    val forcedState: DeviceStateDto?
)

package pl.zarajczyk.familyrules.shared

import org.springframework.stereotype.Service

@Service
class StateService(private val dbConnector: DbConnector) {

    companion object {
        const val DEFAULT_STATE = "ACTIVE"
    }

    fun getFinalDeviceState(instanceId: InstanceId): DeviceState {
        return dbConnector.getForcedInstanceState(instanceId) ?: DEFAULT_STATE
    }

}
package pl.zarajczyk.familyrules.domain

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.AppGroupRepository
import pl.zarajczyk.familyrules.domain.port.AppTechnicalId

@Service
class AutoAddAppsService(
    private val appGroupRepository: AppGroupRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Detects newly seen apps from report payload and automatically adds them to
     * all app groups that this device is enrolled in for auto-adding.
     */
    fun handleReportedApps(
        device: Device,
        reportedAppIds: Set<String>
    ) {
        val newAppIds: Set<AppTechnicalId> = reportedAppIds - device.getDetails().knownApps.keys
        if (newAppIds.isEmpty()) return

        val autoAddGroupIds = device.getDetails().autoAddGroupIds
        if (autoAddGroupIds.isEmpty()) return

        val owner = device.getOwner()
        logger.info("Auto-adding {} new app(s) to {} group(s) for device {}", newAppIds.size, autoAddGroupIds.size, device.getId())

        for (groupId in autoAddGroupIds) {
            try {
                val appGroupRef = appGroupRepository.get(owner.asRef(), groupId) ?: continue
                val currentMembers = appGroupRef.details.members[device.getId().toString()] ?: emptySet()
                val updatedMembers = currentMembers + newAppIds
                if (updatedMembers != currentMembers) {
                    appGroupRepository.setMembers(appGroupRef, device.asRef(), updatedMembers)
                }
            } catch (e: Exception) {
                logger.error("Failed to auto-add apps to group {}: {}", groupId, e.message)
            }
        }
    }
}

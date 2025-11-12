package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.AppGroupRef
import pl.zarajczyk.familyrules.domain.port.AppGroupRepository
import pl.zarajczyk.familyrules.domain.port.AppTechnicalId
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import java.util.UUID

@Service
class AppGroupService(private val devicesRepository: DevicesRepository, private val appGroupRepository: AppGroupRepository) {
    fun <T> withAppGroupContext(user: User, groupId: String, action: (user: AppGroup) -> T): T {
        val ref = appGroupRepository.get(user.asRef(), groupId) ?: throw AppGroupNotFoundException(groupId)
        val user = RefBasedAppGroup(ref, appGroupRepository)
        return action(user)
    }

    fun createAppGroup(user: User, name: String): AppGroup {
        val groupId = UUID.randomUUID().toString()
        val usedColors = listAllAppGroups(user)
            .map { it.get().color }
            .toSet()
        val nextColor = AppGroupColorPalette.getNextColor(usedColors)

        val appGroupRef = appGroupRepository.createAppGroup(user.asRef(), groupId, name, nextColor)

        return RefBasedAppGroup(appGroupRef, appGroupRepository)
    }

    fun listAllAppGroups(user: User): List<AppGroup> {
        return appGroupRepository.getAll(user.asRef())
            .map { RefBasedAppGroup(it, appGroupRepository) }
    }

    fun getReport(
        user: User,
        day: LocalDate
    ): List<AppGroupReport> {
        val devices = devicesRepository.getAll(user.get().username)
        val appGroups = appGroupRepository.getAll(user.asRef())

        val groupStats = appGroups.map { appGroupRef ->
            // Calculate statistics across all instances
            var totalApps = 0
            var totalScreenTimeSeconds = 0L
            val deviceCount = mutableSetOf<String>()
            val appDetails = mutableListOf<AppGroupAppReport>()

            devices.forEach { deviceRef ->
                val instance = devicesRepository.fetchDetails(deviceRef)
                val screenTimeDto = devicesRepository.getScreenTimes(deviceRef, day)
                val appTechnicalIds = appGroupRepository.getMembers(appGroupRef, deviceRef)

                if (appTechnicalIds.isNotEmpty()) {
                    deviceCount.add(instance.id.toString())
                    totalApps += appTechnicalIds.size

                    // Collect detailed app information for this group
                    appTechnicalIds.forEach { appTechnicalId ->
                        val appScreenTimeSeconds = screenTimeDto.applicationsSeconds[appTechnicalId] ?: 0L
                        totalScreenTimeSeconds += appScreenTimeSeconds

                        // Get app name and icon from known apps or use the path
                        val knownApp = instance.knownApps[appTechnicalId]
                        val appName = knownApp?.appName ?: appTechnicalId
                        val appIcon = knownApp?.iconBase64Png

                        appDetails.add(
                            AppGroupAppReport(
                                name = appName,
                                packageName = appTechnicalId,
                                deviceName = instance.name,
                                deviceId = instance.id.toString(),
                                screenTime = appScreenTimeSeconds,
                                percentage = 0.0, // Will be calculated below
                                iconBase64 = appIcon
                            )
                        )
                    }
                }
            }

            // Calculate percentages for each app
            val appsWithPercentages = if (totalScreenTimeSeconds > 0) {
                appDetails.map { app ->
                    app.copy(percentage = (app.screenTime.toDouble() / totalScreenTimeSeconds * 100).let {
                        (it * 100).toInt().toDouble() / 100 // Round to 2 decimal places
                    })
                }
            } else {
                appDetails
            }.sortedByDescending { it.screenTime }

            val group = appGroupRepository.fetchDetails(appGroupRef)
            val colorInfo = AppGroupColorPalette.getColorInfo(group.color)
            AppGroupReport(
                id = group.id,
                name = group.name,
                color = group.color,
                textColor = colorInfo?.text ?: "#000000",
                appsCount = totalApps,
                devicesCount = deviceCount.size,
                totalScreenTime = totalScreenTimeSeconds,
                apps = appsWithPercentages
            )
        }

        return groupStats
    }
}

class AppGroupNotFoundException(groupId: String) : RuntimeException("AppGroup with id $groupId not found")

interface AppGroup {
    fun asRef(): AppGroupRef

    fun get(): AppGroupDto

    fun delete()

    fun rename(newName: String)

    fun containsMember(deviceRef: DeviceRef, appTechnicalId: AppTechnicalId): Boolean

    fun getMembers(deviceRef: DeviceRef): Set<AppTechnicalId>

    fun addMember(deviceRef: DeviceRef, appTechnicalId: AppTechnicalId)

    fun removeMember(deviceRef: DeviceRef, appTechnicalId: AppTechnicalId)
}

data class RefBasedAppGroup(
    val appGroupRef: AppGroupRef,
    private val appGroupRepository: AppGroupRepository
) : AppGroup {
    override fun asRef() = appGroupRef

    override fun get(): AppGroupDto {
        return appGroupRepository.fetchDetails(appGroupRef)
    }

    override fun delete() {
        appGroupRepository.delete(appGroupRef)
    }

    override fun rename(newName: String) {
        appGroupRepository.rename(appGroupRef, newName)
    }

    override fun containsMember(deviceRef: DeviceRef, appTechnicalId: AppTechnicalId): Boolean {
        return getMembers(deviceRef).contains(appTechnicalId)
    }

    override fun getMembers(deviceRef: DeviceRef): Set<AppTechnicalId> {
        return appGroupRepository.getMembers(appGroupRef, deviceRef)
    }

    override fun addMember(deviceRef: DeviceRef, appTechnicalId: AppTechnicalId) {
        appGroupRepository.addMember(appGroupRef, deviceRef, appTechnicalId)
    }

    override fun removeMember(deviceRef: DeviceRef, appTechnicalId: AppTechnicalId) {
        appGroupRepository.removeMember(appGroupRef, deviceRef, appTechnicalId)
    }
}

data class AppGroupReport(
    val id: String,
    val name: String,
    val color: String,
    val textColor: String,
    val appsCount: Int,
    val devicesCount: Int,
    val totalScreenTime: Long,
    val apps: List<AppGroupAppReport> = emptyList()
)

data class AppGroupAppReport(
    val name: String,
    val packageName: String,
    val deviceName: String,
    val deviceId: String,
    val screenTime: Long,
    val percentage: Double,
    val iconBase64: String? = null
)
package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.*
import java.util.*

@Service
class AppGroupService(private val devicesRepository: DevicesRepository, private val appGroupRepository: AppGroupRepository, private val devicesService: DevicesService) {
    fun get(user: User, groupId: String): AppGroup {
        val ref = appGroupRepository.get(user.asRef(), groupId) ?: throw AppGroupNotFoundException(groupId)
        return RefBasedAppGroup(ref, appGroupRepository)
    }

    fun createAppGroup(user: User, name: String): AppGroup {
        val groupId = UUID.randomUUID().toString()
        val usedColors = listAllAppGroups(user)
            .map { it.fetchDetails().color }
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
        val devices = devicesService.getAllDevices(user)
        val appGroups = appGroupRepository.getAll(user.asRef())

        val groupStats = appGroups.map { appGroupRef ->
            // Calculate statistics across all instances
            var totalApps = 0
            var totalScreenTimeSeconds = 0L
            val deviceCount = mutableSetOf<String>()
            val appDetails = mutableListOf<AppGroupAppReport>()

            devices.forEach { device ->
                val instance = device.fetchDetails()
                val screenTimeDto = device.getScreenTimeReport(day)
                val appTechnicalIds = appGroupRepository.getMembers(appGroupRef, device.asRef())

                if (appTechnicalIds.isNotEmpty()) {
                    deviceCount.add(instance.deviceId.toString())
                    totalApps += appTechnicalIds.size

                    // Collect detailed app information for this group
                    appTechnicalIds.forEach { appTechnicalId ->
                        val appScreenTimeSeconds = screenTimeDto.applicationsSeconds[appTechnicalId] ?: 0L
                        totalScreenTimeSeconds += appScreenTimeSeconds

                        // Get app name and icon from known apps or use the path
                        val knownApp = instance.knownApps[appTechnicalId]
                        val appName = knownApp?.appName ?: appTechnicalId
                        val appIcon = knownApp?.iconBase64Png
                        val isOnline = appTechnicalId in screenTimeDto.onlineApps

                        appDetails.add(
                            AppGroupAppReport(
                                name = appName,
                                packageName = appTechnicalId,
                                deviceName = instance.deviceName,
                                deviceId = instance.deviceId.toString(),
                                screenTime = appScreenTimeSeconds,
                                percentage = 0.0, // Will be calculated below
                                iconBase64 = appIcon,
                                online = isOnline
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
            val isGroupOnline = appsWithPercentages.any { it.online }
            AppGroupReport(
                id = group.id,
                name = group.name,
                color = group.color,
                textColor = colorInfo?.text ?: "#000000",
                appsCount = totalApps,
                devicesCount = deviceCount.size,
                totalScreenTime = totalScreenTimeSeconds,
                apps = appsWithPercentages,
                online = isGroupOnline
            )
        }

        return groupStats
    }
}

interface AppGroup {
    fun fetchDetails(): AppGroupDetails

    fun delete()

    fun rename(newName: String)

    fun containsMember(device: Device, appTechnicalId: AppTechnicalId): Boolean

    fun getMembers(device: Device): Set<AppTechnicalId>

    fun addMember(device: Device, appTechnicalId: AppTechnicalId)

    fun removeMember(device: Device, appTechnicalId: AppTechnicalId)
}

data class RefBasedAppGroup(
    val appGroupRef: AppGroupRef,
    private val appGroupRepository: AppGroupRepository
) : AppGroup {
    override fun fetchDetails(): AppGroupDetails {
        return appGroupRepository.fetchDetails(appGroupRef).let {
            AppGroupDetails(it.id, it.name, it.color)
        }
    }

    override fun delete() {
        appGroupRepository.delete(appGroupRef)
    }

    override fun rename(newName: String) {
        appGroupRepository.rename(appGroupRef, newName)
    }

    override fun containsMember(device: Device, appTechnicalId: AppTechnicalId): Boolean {
        return getMembers(device).contains(appTechnicalId)
    }

    override fun getMembers(device: Device): Set<AppTechnicalId> {
        return appGroupRepository.getMembers(appGroupRef, device.asRef())
    }

    override fun addMember(device: Device, appTechnicalId: AppTechnicalId) {
        appGroupRepository.addMember(appGroupRef, device.asRef(), appTechnicalId)
    }

    override fun removeMember(device: Device, appTechnicalId: AppTechnicalId) {
        appGroupRepository.removeMember(appGroupRef, device.asRef(), appTechnicalId)
    }
}

data class AppGroupDetails(
    val id: String,
    val name: String,
    val color: String,
)

data class AppGroupReport(
    val id: String,
    val name: String,
    val color: String,
    val textColor: String,
    val appsCount: Int,
    val devicesCount: Int,
    val totalScreenTime: Long,
    val apps: List<AppGroupAppReport> = emptyList(),
    val online: Boolean = false
)

data class AppGroupAppReport(
    val name: String,
    val packageName: String,
    val deviceName: String,
    val deviceId: String,
    val screenTime: Long,
    val percentage: Double,
    val iconBase64: String? = null,
    val online: Boolean = false
)
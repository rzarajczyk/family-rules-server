package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.*
import java.util.*

@Service
class AppGroupService(private val appGroupRepository: AppGroupRepository, private val devicesService: DevicesService) {
    fun get(user: User, groupId: String): AppGroup {
        val ref = appGroupRepository.get(user.asRef(), groupId) ?: throw AppGroupNotFoundException(groupId)
        return RefBasedAppGroup(ref, appGroupRepository)
    }

    fun createAppGroup(user: User, name: String): AppGroup {
        val groupId = UUID.randomUUID().toString()
        val usedColors = listAllAppGroups(user)
            .map { it.asRef().details.color }
            .toSet()
        val nextColor = AppGroupColorPalette.getNextColor(usedColors)

        val appGroupRef = appGroupRepository.createAppGroup(user.asRef(), groupId, name, nextColor)

        return RefBasedAppGroup(appGroupRef, appGroupRepository)
    }

    fun listAllAppGroups(user: User): List<AppGroup> {
        return appGroupRepository.getAll(user.asRef())
            .map { RefBasedAppGroup(it, appGroupRepository) }
    }

    fun getSimplifiedReport(
        user: User,
        day: LocalDate,
        devices: Map<Device, DeviceDetailsDto>
    ): List<AppGroupSimplifiedReport> {
        val appGroupRefs = appGroupRepository.getAll(user.asRef())

        // Pre-fetch screen time reports once per device (D reads)
        val screenTimeByDeviceId: Map<DeviceId, ScreenReport> = devices.entries.associate { (device, details) ->
            details.deviceId to device.getScreenTimeReport(day)
        }

        return appGroupRefs.map { appGroupRef ->
            val groupDto = appGroupRef.details

            var totalScreenTimeSeconds = 0L
            var isOnline = false

            devices.values.forEach { instance ->
                val screenTimeDto = screenTimeByDeviceId.getValue(instance.deviceId)
                val appTechnicalIds = groupDto.members[instance.deviceId.toString()] ?: emptySet()

                appTechnicalIds.forEach { appTechnicalId ->
                    totalScreenTimeSeconds += screenTimeDto.applicationsSeconds[appTechnicalId] ?: 0L
                    if (!isOnline && appTechnicalId in screenTimeDto.onlineApps) {
                        isOnline = true
                    }
                }
            }

            AppGroupSimplifiedReport(
                id = groupDto.id,
                name = groupDto.name,
                online = isOnline,
                totalScreenTimeSeconds = totalScreenTimeSeconds,
                groupDto = groupDto,
            )
        }
    }

    fun getReport(
        user: User,
        day: LocalDate,
        devicesOverride: List<Device>? = null,
        appGroupsOverride: List<AppGroup>? = null
    ): List<AppGroupReport> {
        val devices = devicesOverride ?: devicesService.getAllDevices(user)
        val appGroups = appGroupsOverride?.map { it.asRef() } ?: appGroupRepository.getAll(user.asRef())

        // Pre-fetch screen time reports once per device (D reads) instead of once per (group × device) (G×D reads)
        val deviceDetails = devices.map { it.getDetails() }
        val screenTimeByDeviceId: Map<DeviceId, ScreenReport> = devices.zip(deviceDetails).associate { (device, details) ->
            details.deviceId to device.getScreenTimeReport(day)
        }

        val groupStats = appGroups.map { appGroupRef ->
            // Use embedded details — no extra Firestore read
            val groupDto = appGroupRef.details

            // Calculate statistics across all instances
            var totalApps = 0
            var totalScreenTimeSeconds = 0L
            val deviceCount = mutableSetOf<String>()
            val appDetails = mutableListOf<AppGroupAppReport>()

            devices.forEachIndexed { index, device ->
                val instance = deviceDetails[index]
                val screenTimeDto = screenTimeByDeviceId.getValue(instance.deviceId)
                // Use embedded members map — no extra Firestore read
                val appTechnicalIds = groupDto.members[instance.deviceId.toString()] ?: emptySet()

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

            val colorInfo = AppGroupColorPalette.getColorInfo(groupDto.color)
            val isGroupOnline = appsWithPercentages.any { it.online }
            AppGroupReport(
                id = groupDto.id,
                name = groupDto.name,
                color = groupDto.color,
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
    fun asRef(): AppGroupRef
    
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
    override fun asRef(): AppGroupRef = appGroupRef
    
    override fun fetchDetails(): AppGroupDetails {
        return AppGroupDetails(appGroupRef.details.id, appGroupRef.details.name, appGroupRef.details.color)
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
        val current = appGroupRepository.getMembers(appGroupRef, device.asRef())
        appGroupRepository.setMembers(appGroupRef, device.asRef(), current + appTechnicalId)
    }

    override fun removeMember(device: Device, appTechnicalId: AppTechnicalId) {
        val current = appGroupRepository.getMembers(appGroupRef, device.asRef())
        appGroupRepository.setMembers(appGroupRef, device.asRef(), current - appTechnicalId)
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

data class AppGroupSimplifiedReport(
    val id: String,
    val name: String,
    val online: Boolean,
    val totalScreenTimeSeconds: Long,
    /** Embedded group DTO — carries members + states, used by callers for currentState computation. Not serialized. */
    val groupDto: AppGroupDto,
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
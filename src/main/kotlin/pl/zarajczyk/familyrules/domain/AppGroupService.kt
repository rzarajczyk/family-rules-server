package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.*
import java.util.*
import java.util.concurrent.Executors

@Service
class AppGroupService(private val appGroupRepository: AppGroupRepository, private val devicesService: DevicesService) {
    fun get(user: User, groupId: String): AppGroup {
        val ref = appGroupRepository.get(user.asRef(), groupId) ?: throw AppGroupNotFoundException(groupId)
        return RefBasedAppGroup(ref, appGroupRepository)
    }

    fun createAppGroup(user: User, name: String): AppGroup {
        val groupId = UUID.randomUUID().toString()
        val appGroupRef = appGroupRepository.createAppGroup(user.asRef(), groupId, name, "")
        return RefBasedAppGroup(appGroupRef, appGroupRepository)
    }

    fun listAllAppGroups(user: User): List<AppGroup> {
        return appGroupRepository.getAll(user.asRef())
            .map { RefBasedAppGroup(it, appGroupRepository) }
    }

    fun getSimplifiedReport(
        user: User,
        day: LocalDate,
        devices: List<Device>
    ): List<AppGroupSimplifiedReport> {
        val appGroups = appGroupRepository.getAll(user.asRef())
        val screenTimeByDeviceId = prefetchScreenTimeReports(devices, day)

        return appGroups.map { appGroupRef ->
            val groupDto = appGroupRef.details

            var totalScreenTimeSeconds = 0L
            var isOnline = false

            devices.forEach { device ->
                val screenTimeDto = screenTimeByDeviceId.getValue(device.getId())
                val appTechnicalIds = groupDto.members[device.getId().toString()] ?: emptySet()

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
        val deviceDetails = devices.map { it.getDetails() }
        val screenTimeByDeviceId = prefetchScreenTimeReports(devices, day)

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
                        val appIcon = knownApp?.iconWebp
                        val isOnline = appTechnicalId in screenTimeDto.onlineApps

                        appDetails.add(
                            AppGroupAppReport(
                                name = appName,
                                packageName = appTechnicalId,
                                deviceName = instance.deviceName,
                                deviceId = instance.deviceId.toString(),
                                screenTime = appScreenTimeSeconds,
                                percentage = 0.0, // Will be calculated below
                                iconWebp = appIcon,
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

            val isGroupOnline = appsWithPercentages.any { it.online }
            AppGroupReport(
                id = groupDto.id,
                name = groupDto.name,
                description = groupDto.description,
                appsCount = totalApps,
                devicesCount = deviceCount.size,
                totalScreenTime = totalScreenTimeSeconds,
                apps = appsWithPercentages,
                online = isGroupOnline
            )
        }

        return groupStats
    }

    private fun prefetchScreenTimeReports(
        devices: List<Device>,
        day: LocalDate,
    ): Map<DeviceId, ScreenReport> {
        if (devices.isEmpty()) return emptyMap()

        val executor = Executors.newVirtualThreadPerTaskExecutor()
        try {
            val futures = devices.associate { device ->
                device.getId() to executor.submit<ScreenReport> { device.getScreenTimeReport(day) }
            }
            return futures.mapValues { (_, future) -> future.get() }
        } finally {
            executor.shutdown()
        }
    }
}

interface AppGroup {
    fun asRef(): AppGroupRef
    
    fun getDetails(): AppGroupDetails

    fun delete()

    fun rename(newName: String)

    fun updateDescription(newDescription: String)

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
    
    override fun getDetails(): AppGroupDetails {
        return AppGroupDetails(appGroupRef.details.id, appGroupRef.details.name, appGroupRef.details.description)
    }

    override fun delete() {
        appGroupRepository.delete(appGroupRef)
    }

    override fun rename(newName: String) {
        appGroupRepository.rename(appGroupRef, newName)
    }

    override fun updateDescription(newDescription: String) {
        appGroupRepository.updateDescription(appGroupRef, newDescription)
    }

    override fun containsMember(device: Device, appTechnicalId: AppTechnicalId): Boolean {
        return getMembers(device).contains(appTechnicalId)
    }

    override fun getMembers(device: Device): Set<AppTechnicalId> {
        return appGroupRef.details.members[device.getId().toString()] ?: emptySet()
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
    val description: String = "",
)

data class AppGroupReport(
    val id: String,
    val name: String,
    val description: String = "",
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
    val iconWebp: ByteArray? = null,
    val online: Boolean = false
)

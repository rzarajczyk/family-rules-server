package pl.zarajczyk.familyrules.domain

import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.springframework.stereotype.Service
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsDto
import pl.zarajczyk.familyrules.domain.port.DeviceDetailsUpdateDto
import pl.zarajczyk.familyrules.domain.port.DeviceRef
import pl.zarajczyk.familyrules.domain.port.DevicesRepository
import java.util.*

@Service
class DevicesService(
    private val devicesRepository: DevicesRepository,
    private val usersService: UsersService
) {
    fun get(deviceId: DeviceId): Device {
        val ref = devicesRepository.get(deviceId) ?: throw DeviceNotFoundException(deviceId)
        return RefBasedDevice(ref, devicesRepository, usersService)
    }

    fun getAllDevices(user: User): List<Device> {
        return devicesRepository
            .getAll(user.asRef())
            .map {
                RefBasedDevice(it, devicesRepository, usersService)
            }
    }

    @Throws(IllegalInstanceName::class, InstanceAlreadyExists::class)
    fun setupNewDevice(username: String, deviceName: String, clientType: String): NewDeviceDetails {
        if (deviceName.length < 3) {
            throw IllegalInstanceName(deviceName)
        }

        val user = usersService.get(username)
        val userRef = user.asRef()
        if (devicesRepository.getByName(userRef, deviceName) != null)
            throw InstanceAlreadyExists(deviceName)

        val deviceId = UUID.randomUUID()
        val token = UUID.randomUUID().toString()
        val details = DeviceDetailsDto(
            deviceId = deviceId,
            deviceName = deviceName,
            forcedDeviceState = null,
            clientType = clientType,
            clientVersion = "v0",
            clientTimezoneOffsetSeconds = 0L,
            iconData = null,
            iconType = null,
            reportIntervalSeconds = 60,
            knownApps = emptyMap(),
            availableDeviceStates = emptyList()
        )

        devicesRepository.createDevice(userRef, details, token.sha256())

        return NewDeviceDetails(
            deviceId = deviceId,
            token = token
        )
    }
}

class IllegalInstanceName(val instanceName: String) : RuntimeException("Instance $instanceName already exists")
class InstanceAlreadyExists(val instanceName: String) : RuntimeException("Instance $instanceName has incorrect name")

data class NewDeviceDetails(
    val deviceId: DeviceId,
    val token: String
)

interface Device {

    fun asRef(): DeviceRef

    fun getId(): DeviceId

    fun validateToken(token: String): Boolean

    fun update(update: DeviceDetailsUpdateDto)

    fun delete()

    fun getOwner(): User

    fun getDetails(): DeviceDetailsDto

    fun getScreenTimeReport(day: LocalDate): ScreenReport

    fun getAppUsageHistogram(day: LocalDate, appTechnicalId: String): Set<String>

    fun getAppUsageBuckets(day: LocalDate): Map<String, Set<String>>

    fun saveScreenTimeReport(day: LocalDate, screenTimeSeconds: Long, applicationsSeconds: Map<String, Long>, activeApps: Set<String>?)

    fun updateOwnerLastActivity(lastActivityMillis: Long)
}

class RefBasedDevice(
    val deviceRef: DeviceRef,
    private val devicesRepository: DevicesRepository,
    private val usersService: UsersService
) : Device {
    private var cachedDetails: DeviceDetailsDto = deviceRef.details

    override fun asRef(): DeviceRef {
        return deviceRef
    }

    override fun getId(): DeviceId = deviceRef.getDeviceId()

    override fun validateToken(token: String): Boolean {
        return deviceRef.tokenHash == token.sha256()
    }

    override fun update(update: DeviceDetailsUpdateDto) {
        devicesRepository.update(deviceRef, update)
    }

    override fun delete() {
        devicesRepository.delete(deviceRef)
    }

    override fun getOwner(): User {
        return usersService.get(devicesRepository.getOwner(deviceRef))
    }

    override fun getDetails(): DeviceDetailsDto {
        return cachedDetails
    }

    override fun getScreenTimeReport(day: LocalDate): ScreenReport {
        val details = getDetails()
        if (details.currentDay == day.toString() && details.currentApplicationTimes != null) {
            val updatedAt = details.currentUpdatedAt ?: Instant.DISTANT_PAST
            val isOnline = updatedAt.isOnline(details.reportIntervalSeconds)
            val onlineApps = if (isOnline) details.currentLastUpdatedApps ?: emptySet() else emptySet()
            return ScreenReport(
                screenTimeSeconds = details.currentScreenTime ?: 0L,
                applicationsSeconds = details.currentApplicationTimes,
                appUsageBuckets = emptyMap(),
                updatedAt = updatedAt,
                onlinePeriods = details.currentOnlinePeriods ?: emptySet(),
                lastUpdatedApps = details.currentLastUpdatedApps ?: emptySet(),
                online = isOnline,
                onlineApps = onlineApps
            )
        }
        val reportIntervalSeconds = details.reportIntervalSeconds
        return (devicesRepository.getScreenReport(deviceRef, day) ?: ScreenReportDto.empty()).toDomain(
            reportIntervalSeconds
        )
    }

    override fun getAppUsageHistogram(day: LocalDate, appTechnicalId: String): Set<String> {
        return devicesRepository.getAppUsageHistogram(deviceRef, day, appTechnicalId)
    }

    override fun getAppUsageBuckets(day: LocalDate): Map<String, Set<String>> {
        return devicesRepository.getScreenReport(deviceRef, day)?.appUsageBuckets ?: emptyMap()
    }

    private fun ScreenReportDto.toDomain(reportIntervalSeconds: Long): ScreenReport {
        val isOnline = updatedAt.isOnline(reportIntervalSeconds)
        val onlineApps = if (isOnline) lastUpdatedApps else emptySet()
        return ScreenReport(
            screenTimeSeconds = screenTimeSeconds,
            applicationsSeconds = applicationsSeconds,
            appUsageBuckets = appUsageBuckets,
            updatedAt = updatedAt,
            onlinePeriods = onlinePeriods,
            lastUpdatedApps = lastUpdatedApps,
            online = isOnline,
            onlineApps = onlineApps
        )
    }

    private fun Instant.isOnline(reportIntervalSeconds: Long) =
        (Clock.System.now() - this).inWholeSeconds <= reportIntervalSeconds

    override fun saveScreenTimeReport(
        day: LocalDate,
        screenTimeSeconds: Long,
        applicationsSeconds: Map<String, Long>,
        activeApps: Set<String>?,
    ) {
        val details = getDetails()
        val previousApplicationTimes = if (details.currentDay == day.toString()) {
            details.currentApplicationTimes ?: emptyMap()
        } else {
            emptyMap()
        }

        val currentAppBucketDeltas = applicationsSeconds
            .mapNotNull { (appId, seconds) ->
                val delta = seconds - (previousApplicationTimes[appId] ?: 0L)
                if (delta > 0L) appId to delta else null
            }
            .toMap()

        val lastUpdatedApps: Set<String> = if (activeApps != null) {
            activeApps
        } else {
            currentAppBucketDeltas.keys
        }

        val now = Clock.System.now()

        val onlinePeriodBucket = now.toBucket()

        val previousOnlinePeriods: Set<String> = if (details.currentDay == day.toString()) {
            details.currentOnlinePeriods ?: emptySet()
        } else {
            emptySet()
        }
        val currentOnlinePeriods: Set<String> = previousOnlinePeriods + onlinePeriodBucket

        devicesRepository.setScreenReport(
            device = deviceRef,
            day = day,
            screenReportDto = SetScreenReportDto(
                screenTimeSeconds = screenTimeSeconds,
                applicationsSeconds = applicationsSeconds,
                updatedAt = now,
                currentOnlinePeriodBucket = onlinePeriodBucket,
                currentOnlinePeriods = currentOnlinePeriods,
                lastUpdatedApps = lastUpdatedApps,
                currentAppBucketDeltas = currentAppBucketDeltas,
            )
        )

        devicesRepository.setCurrentScreenReport(
            device = deviceRef,
            day = day,
            screenReportDto = SetScreenReportDto(
                screenTimeSeconds = screenTimeSeconds,
                applicationsSeconds = applicationsSeconds,
                updatedAt = now,
                currentOnlinePeriodBucket = onlinePeriodBucket,
                currentOnlinePeriods = currentOnlinePeriods,
                lastUpdatedApps = lastUpdatedApps,
                currentAppBucketDeltas = currentAppBucketDeltas,
            )
        )

        cachedDetails = cachedDetails.copy(
            currentDay = day.toString(),
            currentScreenTime = screenTimeSeconds,
            currentApplicationTimes = applicationsSeconds,
            currentUpdatedAt = now,
            currentLastUpdatedApps = lastUpdatedApps,
            currentOnlinePeriods = currentOnlinePeriods,
        )
    }

    override fun updateOwnerLastActivity(lastActivityMillis: Long) {
        devicesRepository.updateOwnerLastActivity(deviceRef, lastActivityMillis)
    }

    private fun Instant.toBucket(): String {
        val localDateTime = this.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minuteBucket = (localDateTime.minute / 10) * 10
        val minuteStr = minuteBucket.toString().padStart(2, '0')
        return "$hour:$minuteStr"
    }
}


data class ScreenReport(
    val screenTimeSeconds: Long,
    val applicationsSeconds: Map<String, Long>,
    val appUsageBuckets: Map<String, Set<String>>,
    val updatedAt: Instant,
    val onlinePeriods: Set<String>,
    val lastUpdatedApps: Set<String>,
    val online: Boolean,
    val onlineApps: Set<String>
)

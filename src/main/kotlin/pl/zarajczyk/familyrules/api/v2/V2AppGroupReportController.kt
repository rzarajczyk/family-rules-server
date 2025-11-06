package pl.zarajczyk.familyrules.api.v2

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pl.zarajczyk.familyrules.domain.*

@RestController
class V2AppGroupReportController(private val dataRepository: DataRepository) {
    @GetMapping("/api/v2/group-report")
    fun appGroupReport(@RequestBody request: AppGroupReportRequest, authentication: Authentication): AppGroupReportResponse {
        val instanceRef = dataRepository.findAuthenticatedInstance(authentication)
        val instance = dataRepository.getInstance(instanceRef)

        val memberships = dataRepository.getAppGroupMemberships(instanceRef)
        val groupMemberships = memberships.filter { it.groupId == request.appGroupId }

        val apps = groupMemberships.associate { membership ->
            val known = instance.knownApps[membership.appPath]
            membership.appPath to App(
                appName = known?.appName ?: membership.appPath,
                iconBase64Png = known?.iconBase64Png
            )
        }

        return AppGroupReportResponse(
            appGroupId = request.appGroupId,
            apps = apps
        )
    }
}

data class AppGroupReportRequest(
    val appGroupId: String
)

data class AppGroupReportResponse(
    val appGroupId: String,
    val apps: Map<String, App>
)

package pl.zarajczyk.familyrules.domain.port

enum class ForceReportPushStatus {
    SUCCESS,
    NOT_CAPABLE,
    NO_TOKEN,
    UNSUPPORTED_CAPABILITY,
    PROVIDER_ERROR,
    NOT_CONFIGURED,
}

data class ForceReportPushResult(
    val status: ForceReportPushStatus,
    val message: String? = null,
    val clearStoredToken: Boolean = false,
)

interface ForceReportPushSender {
    fun supportedCapability(): String
    fun sendForceReport(pushToken: String): ForceReportPushResult
}

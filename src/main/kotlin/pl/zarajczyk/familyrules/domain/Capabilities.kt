package pl.zarajczyk.familyrules.domain

object Capability {
    const val LOGS_COMMAND = "LOGS_COMMAND"
    const val SEND_LOGS_COMMAND = "SEND_LOGS_COMMAND"
    const val DISABLE_COMMAND = "DISABLE_COMMAND"
    const val UNINSTALL_COMMAND = "UNINSTALL_COMMAND"
    const val PLAY_LOUD_SOUND_COMMAND = "PLAY_LOUD_SOUND_COMMAND"
    const val COMMANDS_PULL = "COMMANDS_PULL"
    const val LOCATION_REPORT = "LOCATION_REPORT"
    const val MEDIA_PLAYBACK_REPORT = "MEDIA_PLAYBACK_REPORT"
    const val MEDIA_PLAYBACK_BLOCK = "MEDIA_PLAYBACK_BLOCK"
    const val RESTRICTED_APPS_BLOCK = "RESTRICTED_APPS_BLOCK"
    const val FULL_DEVICE_BLOCK = "FULL_DEVICE_BLOCK"
    const val ALL_MY_DEVICES_DISPLAY = "ALL_MY_DEVICES_DISPLAY"
    const val FCM_FORCE_REPORT_PUSH = "FCM_FORCE_REPORT_PUSH"
    const val APNS_FORCE_REPORT_PUSH = "APNS_FORCE_REPORT_PUSH"
}

val FORCE_REPORT_PUSH_CAPABILITIES: Set<String> = setOf(
    Capability.FCM_FORCE_REPORT_PUSH,
    Capability.APNS_FORCE_REPORT_PUSH,
)

fun forceReportPushCapabilities(capabilities: List<String>): List<String> =
    capabilities.filter { it in FORCE_REPORT_PUSH_CAPABILITIES }

fun deviceForceReportPushCapability(capabilities: List<String>): String? =
    forceReportPushCapabilities(capabilities).singleOrNull()

fun validateForceReportPushCapabilities(capabilities: List<String>) {
    if (forceReportPushCapabilities(capabilities).size > 1) {
        throw IllegalArgumentException("Multiple force-report push capabilities are not allowed")
    }
}

val COMMAND_CAPABILITY: Map<String, String> = mapOf(
    "SEND_LOGS" to Capability.LOGS_COMMAND,
    "DISABLE" to Capability.DISABLE_COMMAND,
    "UNINSTALL" to Capability.UNINSTALL_COMMAND,
    "PLAY_LOUD_SOUND" to Capability.PLAY_LOUD_SOUND_COMMAND,
)

private val CAPABILITY_SYNONYMS: Map<String, String> = mapOf(
    Capability.SEND_LOGS_COMMAND to Capability.LOGS_COMMAND,
)

fun deviceHasCapability(capabilities: List<String>, required: String): Boolean {
    if (required in capabilities) return true
    return capabilities.any { CAPABILITY_SYNONYMS[it] == required }
}

fun deriveCapabilitiesFromCommands(commands: List<String>): List<String> {
    if (commands.isEmpty()) return emptyList()
    val capabilities = commands.mapNotNull { COMMAND_CAPABILITY[it] }.toMutableSet()
    capabilities.add(Capability.COMMANDS_PULL)
    return capabilities.toList()
}

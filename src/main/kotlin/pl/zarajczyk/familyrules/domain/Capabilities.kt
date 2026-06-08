package pl.zarajczyk.familyrules.domain

object Capability {
    const val LOGS_COMMAND = "LOGS_COMMAND"
    const val SEND_LOGS_COMMAND = "SEND_LOGS_COMMAND"
    const val DISABLE_COMMAND = "DISABLE_COMMAND"
    const val UNINSTALL_COMMAND = "UNINSTALL_COMMAND"
    const val COMMANDS_PULL = "COMMANDS_PULL"
    const val LOCATION_REPORT = "LOCATION_REPORT"
    const val MEDIA_PLAYBACK_REPORT = "MEDIA_PLAYBACK_REPORT"
    const val MEDIA_PLAYBACK_BLOCK = "MEDIA_PLAYBACK_BLOCK"
    const val ALL_MY_DEVICES_DISPLAY = "ALL_MY_DEVICES_DISPLAY"
}

val COMMAND_CAPABILITY: Map<String, String> = mapOf(
    "SEND_LOGS" to Capability.LOGS_COMMAND,
    "DISABLE" to Capability.DISABLE_COMMAND,
    "UNINSTALL" to Capability.UNINSTALL_COMMAND,
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

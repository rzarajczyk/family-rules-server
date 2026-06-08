package pl.zarajczyk.familyrules.domain

object Capability {
    const val LOGS_COMMAND = "LOGS_COMMAND"
    const val DISABLE_COMMAND = "DISABLE_COMMAND"
    const val UNINSTALL_COMMAND = "UNINSTALL_COMMAND"
    const val COMMANDS_PULL = "COMMANDS_PULL"
    const val LOCATION_REPORT = "LOCATION_REPORT"
    const val MEDIA_PLAYBACK_REPORT = "MEDIA_PLAYBACK_REPORT"
    const val MEDIA_PLAYBACK_BLOCK = "MEDIA_PLAYBACK_BLOCK"
}

val COMMAND_CAPABILITY: Map<String, String> = mapOf(
    "SEND_LOGS" to Capability.LOGS_COMMAND,
    "DISABLE" to Capability.DISABLE_COMMAND,
    "UNINSTALL" to Capability.UNINSTALL_COMMAND,
)

fun deriveCapabilitiesFromCommands(commands: List<String>): List<String> {
    if (commands.isEmpty()) return emptyList()
    val capabilities = commands.mapNotNull { COMMAND_CAPABILITY[it] }.toMutableSet()
    capabilities.add(Capability.COMMANDS_PULL)
    return capabilities.toList()
}

package pl.zarajczyk.familyrules

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import pl.zarajczyk.familyrules.domain.Capability
import pl.zarajczyk.familyrules.domain.deriveCapabilitiesFromCommands

class CapabilitiesTest : FunSpec({

    test("deriveCapabilitiesFromCommands maps SEND_LOGS to LOGS_COMMAND and COMMANDS_PULL") {
        deriveCapabilitiesFromCommands(listOf("SEND_LOGS")) shouldContainExactlyInAnyOrder listOf(
            Capability.LOGS_COMMAND,
            Capability.COMMANDS_PULL,
        )
    }

    test("deriveCapabilitiesFromCommands returns empty list for no commands") {
        deriveCapabilitiesFromCommands(emptyList()) shouldBe emptyList()
    }

    test("deriveCapabilitiesFromCommands ignores unknown commands") {
        deriveCapabilitiesFromCommands(listOf("UNKNOWN")) shouldContainExactlyInAnyOrder listOf(
            Capability.COMMANDS_PULL,
        )
    }
})

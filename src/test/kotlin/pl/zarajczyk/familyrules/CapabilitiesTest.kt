package pl.zarajczyk.familyrules

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import pl.zarajczyk.familyrules.domain.Capability
import pl.zarajczyk.familyrules.domain.deriveCapabilitiesFromCommands
import pl.zarajczyk.familyrules.domain.deviceForceReportPushCapability
import pl.zarajczyk.familyrules.domain.deviceHasCapability
import pl.zarajczyk.familyrules.domain.validateForceReportPushCapabilities

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

    test("deriveCapabilitiesFromCommands maps DISABLE to DISABLE_COMMAND and COMMANDS_PULL") {
        deriveCapabilitiesFromCommands(listOf("DISABLE")) shouldContainExactlyInAnyOrder listOf(
            Capability.DISABLE_COMMAND,
            Capability.COMMANDS_PULL,
        )
    }

    test("deriveCapabilitiesFromCommands maps UNINSTALL to UNINSTALL_COMMAND and COMMANDS_PULL") {
        deriveCapabilitiesFromCommands(listOf("UNINSTALL")) shouldContainExactlyInAnyOrder listOf(
            Capability.UNINSTALL_COMMAND,
            Capability.COMMANDS_PULL,
        )
    }

    test("deviceHasCapability accepts SEND_LOGS_COMMAND as synonym for LOGS_COMMAND") {
        deviceHasCapability(listOf(Capability.SEND_LOGS_COMMAND), Capability.LOGS_COMMAND) shouldBe true
        deviceHasCapability(listOf(Capability.LOGS_COMMAND), Capability.LOGS_COMMAND) shouldBe true
        deviceHasCapability(listOf(Capability.COMMANDS_PULL), Capability.LOGS_COMMAND) shouldBe false
    }

    test("deviceForceReportPushCapability returns single force-report capability") {
        deviceForceReportPushCapability(listOf(Capability.FCM_FORCE_REPORT_PUSH, Capability.COMMANDS_PULL)) shouldBe
            Capability.FCM_FORCE_REPORT_PUSH
    }

    test("validateForceReportPushCapabilities rejects multiple force-report capabilities") {
        shouldThrow<IllegalArgumentException> {
            validateForceReportPushCapabilities(
                listOf(Capability.FCM_FORCE_REPORT_PUSH, Capability.APNS_FORCE_REPORT_PUSH)
            )
        }
    }
})

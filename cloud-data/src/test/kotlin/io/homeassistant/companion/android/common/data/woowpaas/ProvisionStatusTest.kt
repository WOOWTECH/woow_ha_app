package io.homeassistant.companion.android.common.data.woowpaas

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Pins down the mapping between the six status values of the backend contract and [ProvisionStatus].
 */
class ProvisionStatusTest {

    @ParameterizedTest
    @CsvSource("none", "provisioning", "ready", "error", "suspended", "deleting")
    fun `Given a documented status when parsing then the raw value survives the round trip`(rawValue: String) {
        assertEquals(rawValue, ProvisionStatus.from(rawValue).rawValue)
    }

    @Test
    fun `Given the six documented statuses when parsing then each maps to its own case`() {
        assertEquals(ProvisionStatus.None, ProvisionStatus.from("none"))
        assertEquals(ProvisionStatus.Provisioning, ProvisionStatus.from("provisioning"))
        assertEquals(ProvisionStatus.Ready, ProvisionStatus.from("ready"))
        assertEquals(ProvisionStatus.Error, ProvisionStatus.from("error"))
        assertEquals(ProvisionStatus.Suspended, ProvisionStatus.from("suspended"))
        assertEquals(ProvisionStatus.Deleting, ProvisionStatus.from("deleting"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["upgrading", "READY", "", " ready"])
    fun `Given a status this version does not know when parsing then it falls back to Unknown`(rawValue: String) {
        assertEquals(ProvisionStatus.Unknown(rawValue), ProvisionStatus.from(rawValue))
    }
}

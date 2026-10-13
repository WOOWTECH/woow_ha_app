package io.homeassistant.companion.android.matter

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class MatterManagerImplTest {
    private val manager = MatterManagerImpl()

    @Test
    fun `Given woowtech aiot when checking Matter capabilities then commissioning is unsupported`() = runTest {
        assertFalse(manager.appSupportsCommissioning())
        assertFalse(manager.coreSupportsCommissioning(serverId = 1))
        assertNull(manager.commissionDevice(code = "test-code", serverId = 1))
        assertNull(manager.commissionOnNetworkDevice(pin = 1L, ip = "192.0.2.1", serverId = 1))
    }

    @Test
    fun `Given woowtech aiot when requesting commissioning then reports unsupported without launching Google`() {
        var failure: Exception? = null
        manager.startNewCommissioningFlow(
            context = mockk<Context>(),
            onSuccess = { fail<Unit>("Commissioning must not launch") },
            onFailure = { failure = it },
        )
        assertInstanceOf(IllegalStateException::class.java, failure)
    }
}

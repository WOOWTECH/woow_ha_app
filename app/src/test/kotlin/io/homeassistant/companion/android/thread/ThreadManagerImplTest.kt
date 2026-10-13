package io.homeassistant.companion.android.thread

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ThreadManagerImplTest {
    private val manager = ThreadManagerImpl()

    @Test
    fun `Given woowtech aiot when checking Thread capabilities then credentials are unavailable`() = runTest {
        assertFalse(manager.appSupportsThread())
        assertFalse(manager.coreSupportsThread(serverId = 1))
        assertNull(manager.getPreferredDatasetFromServer(serverId = 1))
        assertEquals(
            ThreadManager.SyncResult.AppUnsupported,
            manager.syncPreferredDataset(
                context = mockk<Context>(),
                serverId = 1,
                exportOnly = false,
                scope = backgroundScope,
            ),
        )
    }
}

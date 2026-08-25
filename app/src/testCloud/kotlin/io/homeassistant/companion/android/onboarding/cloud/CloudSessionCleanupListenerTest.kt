package io.homeassistant.companion.android.onboarding.cloud

import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSessionRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CloudSessionCleanupListenerTest {

    private val sessionRepository: WoowPaasSessionRepository = mockk()
    private val listener = CloudSessionCleanupListener(sessionRepository)

    @Test
    fun `Given a registered server when notified then the PaaS session is discarded`() = runTest {
        coEvery { sessionRepository.clearSession() } just Runs

        listener.onServerRegistered()

        coVerify(exactly = 1) { sessionRepository.clearSession() }
    }

    @Test
    fun `Given the storage fails when notified then the failure is swallowed`() = runTest {
        coEvery { sessionRepository.clearSession() } throws IOException("disk gone")

        // Must not throw: a storage failure here would otherwise revert a registration that
        // actually succeeded, only to preserve credentials nobody reads.
        listener.onServerRegistered()
    }
}

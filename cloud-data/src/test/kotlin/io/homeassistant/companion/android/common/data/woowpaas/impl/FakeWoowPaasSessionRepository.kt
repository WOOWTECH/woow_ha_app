package io.homeassistant.companion.android.common.data.woowpaas.impl

import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSession
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSessionRepository
import kotlin.time.ExperimentalTime

/**
 * In memory [WoowPaasSessionRepository] for the tests that care about what ends up stored rather than
 * about how it is serialized.
 *
 * [session] is exposed so a test can seed a starting point and read back what a rotation produced.
 */
@OptIn(ExperimentalTime::class)
internal class FakeWoowPaasSessionRepository(@Volatile var session: WoowPaasSession? = null) : WoowPaasSessionRepository {

    override suspend fun currentSession(): WoowPaasSession? = session

    override suspend fun saveSession(session: WoowPaasSession) {
        this.session = session
    }

    override suspend fun clearSession() {
        session = null
    }
}

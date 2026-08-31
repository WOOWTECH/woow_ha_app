package io.homeassistant.companion.android.common.data.woowpaas.impl

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.woowpaas.NamedWoowPaasStorage
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSession
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSessionRepository
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * The single key the whole session is stored under.
 *
 * One key instead of one per field is what makes a rotation atomic: the storage either holds the previous
 * session or the new one, never an access token paired with the refresh token it just invalidated.
 */
private const val KEY_SESSION = "session"

/** Storage representation of a [WoowPaasSession]. */
@Serializable
private data class StoredSession(
    val accessToken: String,
    val refreshToken: String? = null,
    val accessTokenExpiresAtEpochMillis: Long,
)

/**
 * [WoowPaasSessionRepository] backed by the shared preferences file dedicated to WOOW PaaS.
 *
 * Reads and writes are serialized through a [Mutex] so a rotation happening while another caller reads
 * the session cannot be observed halfway through.
 *
 * Writes go through shared preferences, which commit asynchronously and report no failure, so this
 * implementation never throws the `IOException` [WoowPaasSessionRepository.saveSession] allows for.
 */
@OptIn(ExperimentalTime::class)
internal class WoowPaasSessionRepositoryImpl @Inject constructor(
    @NamedWoowPaasStorage private val localStorage: LocalStorage,
) : WoowPaasSessionRepository {

    private val mutex = Mutex()

    override suspend fun currentSession(): WoowPaasSession? = mutex.withLock {
        val payload = localStorage.getString(KEY_SESSION) ?: return null
        try {
            woowPaasJsonMapper.decodeFromString<StoredSession>(payload).toSession()
        } catch (e: SerializationException) {
            // Nothing can be recovered from an unreadable payload; the user goes through the device flow
            // again and the next successful login overwrites it.
            Timber.e(e, "Stored WOOW PaaS session cannot be read back")
            null
        }
    }

    override suspend fun saveSession(session: WoowPaasSession) {
        mutex.withLock {
            localStorage.putString(KEY_SESSION, woowPaasJsonMapper.encodeToString(session.toStored()))
        }
    }

    override suspend fun clearSession() {
        mutex.withLock { localStorage.remove(KEY_SESSION) }
    }
}

@OptIn(ExperimentalTime::class)
private fun WoowPaasSession.toStored() = StoredSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    accessTokenExpiresAtEpochMillis = accessTokenExpiresAt.toEpochMilliseconds(),
)

@OptIn(ExperimentalTime::class)
private fun StoredSession.toSession() = WoowPaasSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    accessTokenExpiresAt = Instant.fromEpochMilliseconds(accessTokenExpiresAtEpochMillis),
)

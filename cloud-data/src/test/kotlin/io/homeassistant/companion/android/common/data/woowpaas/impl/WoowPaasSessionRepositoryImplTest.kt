package io.homeassistant.companion.android.common.data.woowpaas.impl

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSession
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val EXPIRES_AT = Instant.fromEpochMilliseconds(3_600_000)

/**
 * In memory [LocalStorage] behaving like the shared preferences backed one, with a hook to make a write
 * fail so the atomicity of a rotation can be observed.
 */
private class FakeLocalStorage : LocalStorage {
    val values = mutableMapOf<String, String?>()
    var failOnPut = false
    var putCount = 0

    override suspend fun putString(key: String, value: String?) {
        if (failOnPut) throw IOException("storage is full")
        putCount++
        values[key] = value
    }

    override suspend fun getString(key: String): String? = values[key]

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun putLong(key: String, value: Long?) = unsupported()
    override suspend fun getLong(key: String): Long? = unsupported()
    override suspend fun putInt(key: String, value: Int?) = unsupported()
    override suspend fun getInt(key: String): Int? = unsupported()
    override suspend fun putBoolean(key: String, value: Boolean) = unsupported()
    override suspend fun getBoolean(key: String): Boolean = unsupported()
    override suspend fun getBooleanOrNull(key: String): Boolean? = unsupported()
    override suspend fun putStringSet(key: String, value: Set<String>) = unsupported()
    override suspend fun getStringSet(key: String): Set<String>? = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Not used by the session storage")
}

@OptIn(ExperimentalTime::class)
class WoowPaasSessionRepositoryImplTest {

    private val storage = FakeLocalStorage()
    private val repository = WoowPaasSessionRepositoryImpl(storage)

    @Test
    fun `Given no stored session when reading it then nothing is returned`() = runTest {
        assertNull(repository.currentSession())
    }

    @Test
    fun `Given a saved session when reading it back then every field survived the round trip`() = runTest {
        val session = session(refreshToken = "refresh-1")

        repository.saveSession(session)

        assertEquals(session, repository.currentSession())
    }

    @Test
    fun `Given a saved session without a refresh token when reading it back then the absence survived`() = runTest {
        val session = session(refreshToken = null)

        repository.saveSession(session)

        assertEquals(session, repository.currentSession())
    }

    @Test
    fun `Given a session when saving it then it reaches the storage as a single write`() = runTest {
        repository.saveSession(session(refreshToken = "refresh-1"))

        // Splitting the session over several keys would let a crash leave an access token next to the
        // refresh token it invalidated.
        assertEquals(1, storage.putCount)
        assertEquals(1, storage.values.size)
    }

    @Test
    fun `Given a saved session when clearing it then nothing is left behind`() = runTest {
        repository.saveSession(session(refreshToken = "refresh-1"))

        repository.clearSession()

        assertNull(repository.currentSession())
        assertEquals(emptyMap<String, String?>(), storage.values)
    }

    @Test
    fun `Given a stored payload that is not readable when reading the session then nothing is returned`() = runTest {
        repository.saveSession(session(refreshToken = "refresh-1"))
        storage.values.keys.forEach { storage.values[it] = "{not json" }

        assertNull(repository.currentSession())
    }

    @Test
    fun `Given a rotation whose write fails when reading the session then the previous one is intact`() = runTest {
        val previous = session(accessToken = "access-1", refreshToken = "refresh-1")
        repository.saveSession(previous)
        storage.failOnPut = true

        val failure = runCatching {
            repository.saveSession(session(accessToken = "access-2", refreshToken = "refresh-2"))
        }.exceptionOrNull()

        // The whole session is written as one value: a failed rotation cannot leave the new access token
        // next to the refresh token it invalidated.
        assertInstanceOf(IOException::class.java, failure)
        assertEquals(previous, repository.currentSession())
    }

    @Test
    fun `Given concurrent rotations when they all completed then the storage holds exactly one of them`() = runTest {
        val sessions = (1..10).map { session(accessToken = "access-$it", refreshToken = "refresh-$it") }

        sessions.map { async { repository.saveSession(it) } }.awaitAll()

        assertEquals(1, storage.values.size)
        assertTrue(repository.currentSession() in sessions)
    }

    private fun session(accessToken: String = "access-1", refreshToken: String?) = WoowPaasSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        accessTokenExpiresAt = EXPIRES_AT,
    )
}

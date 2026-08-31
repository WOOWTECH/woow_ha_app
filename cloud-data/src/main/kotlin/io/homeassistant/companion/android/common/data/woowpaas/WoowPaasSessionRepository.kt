package io.homeassistant.companion.android.common.data.woowpaas

/**
 * Persistent home of the WOOW PaaS credentials, so the cloud onboarding flow survives a process death.
 *
 * The session is stored as a whole: the backend rotates the access and the refresh token together, and a
 * half written session would leave the application with an access token whose refresh token is already
 * invalid. Implementations therefore replace or drop the session atomically, and serialize concurrent
 * writes.
 *
 * Every function performs storage access on a background dispatcher and is safe to call from the main
 * thread.
 */
interface WoowPaasSessionRepository {

    /**
     * Returns the stored session, or null when there is none or when what is stored cannot be read back.
     */
    suspend fun currentSession(): WoowPaasSession?

    /**
     * Replaces the stored session with [session].
     *
     * @throws java.io.IOException when the storage refuses the write, leaving the previously stored session
     * intact. The implementation shipped today never reports such a failure: it writes through shared
     * preferences, whose asynchronous commit has no way to signal one. The contract is stated so callers
     * are not written against that accident, because a storage that does report failures (an encrypted
     * `DataStore`, for instance) would be a drop-in replacement.
     */
    suspend fun saveSession(session: WoowPaasSession)

    /**
     * Drops the stored session, for instance once the Home Assistant server it produced is registered in
     * the application, or when the backend refused to refresh it.
     */
    suspend fun clearSession()
}

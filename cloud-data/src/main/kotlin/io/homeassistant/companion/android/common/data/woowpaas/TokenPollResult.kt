package io.homeassistant.companion.android.common.data.woowpaas

/**
 * Outcome of a single poll of the OAuth 2.0 device flow token endpoint (RFC 8628 §3.4).
 *
 * The distinction that matters to callers is whether the flow may continue: [Pending], [SlowDown] and
 * [TransientError] all mean "keep polling", while [Success] and [Failed] end the loop.
 */
sealed interface TokenPollResult {

    /**
     * The user authorized the device, and the session that was issued is already persisted.
     *
     * The credentials themselves are not reported: they live in the
     * [WoowPaasSessionRepository] so there is a single place holding them.
     */
    data object Success : TokenPollResult

    /** The user has not authorized the device yet. */
    data object Pending : TokenPollResult

    /** The backend asked the client to poll less often; [newInterval] is the new delay in seconds. */
    data class SlowDown(val newInterval: Int) : TokenPollResult

    /**
     * A retryable failure such as a network hiccup or an HTTP 5xx response.
     * Per RFC 8628 §3.4 the client should keep polling instead of aborting the flow.
     */
    data class TransientError(val reason: String) : TokenPollResult

    /**
     * A terminal failure such as `access_denied`, `expired_token` or any other OAuth error.
     * Polling must stop.
     */
    data class Failed(val error: String) : TokenPollResult
}

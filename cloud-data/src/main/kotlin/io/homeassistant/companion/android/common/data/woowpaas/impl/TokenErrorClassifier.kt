package io.homeassistant.companion.android.common.data.woowpaas.impl

import io.homeassistant.companion.android.common.data.woowpaas.TokenPollResult
import java.io.IOException
import kotlinx.coroutines.CancellationException

private const val SLOW_DOWN_INTERVAL_INCREMENT_SECONDS = 5

/** The status codes that say something went wrong on the backend side rather than with the request. */
internal val HTTP_SERVER_ERROR_RANGE = 500..599

/** The OAuth 2.0 device flow error codes that keep the polling loop alive (RFC 8628 §3.5). */
private object OAuthDeviceFlowError {
    const val AUTHORIZATION_PENDING = "authorization_pending"
    const val SLOW_DOWN = "slow_down"
}

/**
 * Classifies a non-successful token endpoint response into a [TokenPollResult].
 *
 * Follows RFC 8628 §3.5: `authorization_pending` and `slow_down` keep the flow alive, an HTTP 5xx is
 * a transient server-side failure worth retrying, and every other OAuth error (`access_denied`,
 * `expired_token`, …) is terminal.
 *
 * @param httpCode the HTTP status code of the response
 * @param error the parsed OAuth `error` field, or null when it is absent or unparseable
 * @param errorDescription the parsed OAuth `error_description` field, if any
 * @param currentInterval the current polling interval in seconds, used to compute the slow-down backoff
 */
internal fun classifyTokenError(
    httpCode: Int,
    error: String?,
    errorDescription: String?,
    currentInterval: Int,
): TokenPollResult {
    if (httpCode in HTTP_SERVER_ERROR_RANGE) {
        return TokenPollResult.TransientError("伺服器暫時無法回應 (HTTP $httpCode)")
    }
    return when (error) {
        OAuthDeviceFlowError.AUTHORIZATION_PENDING -> TokenPollResult.Pending
        OAuthDeviceFlowError.SLOW_DOWN -> TokenPollResult.SlowDown(
            currentInterval + SLOW_DOWN_INTERVAL_INCREMENT_SECONDS,
        )
        else -> TokenPollResult.Failed(errorDescription ?: error ?: "授權失敗")
    }
}

/**
 * Classifies an exception raised while polling the token endpoint.
 *
 * Network-layer failures ([IOException] and its subtypes such as [java.net.SocketTimeoutException] and
 * [java.net.UnknownHostException]) are transient and worth retrying; anything else is terminal. A
 * [CancellationException] is re-thrown so structured coroutine cancellation is never swallowed.
 */
internal fun classifyTokenException(e: Exception): TokenPollResult {
    if (e is CancellationException) throw e
    return when (e) {
        is IOException -> TokenPollResult.TransientError(e.message ?: "網路連線中斷")
        else -> TokenPollResult.Failed(e.message ?: "登入發生錯誤")
    }
}

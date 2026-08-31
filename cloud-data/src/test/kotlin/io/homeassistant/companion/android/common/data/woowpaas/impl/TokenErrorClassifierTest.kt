package io.homeassistant.companion.android.common.data.woowpaas.impl

import io.homeassistant.companion.android.common.data.woowpaas.TokenPollResult
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Unit tests for the pure classification helpers backing the device flow token polling.
 *
 * The device flow token endpoint (RFC 8628 §3.5) distinguishes retryable failures
 * (network hiccups, HTTP 5xx) from terminal ones (access_denied, expired_token).
 * These tests pin that mapping down without touching the network.
 */
class TokenErrorClassifierTest {

    @Test
    fun `Given HTTP 5xx when classifyTokenError then result is TransientError`() {
        val result = classifyTokenError(httpCode = 503, error = null, errorDescription = null, currentInterval = 5)

        assertInstanceOf(TokenPollResult.TransientError::class.java, result)
    }

    @Test
    fun `Given HTTP 5xx with an error body when classifyTokenError then 5xx still wins as TransientError`() {
        val result = classifyTokenError(httpCode = 500, error = "server_error", errorDescription = "boom", currentInterval = 5)

        assertInstanceOf(TokenPollResult.TransientError::class.java, result)
    }

    @Test
    fun `Given authorization_pending when classifyTokenError then result is Pending`() {
        val result = classifyTokenError(httpCode = 400, error = "authorization_pending", errorDescription = null, currentInterval = 5)

        assertEquals(TokenPollResult.Pending, result)
    }

    @Test
    fun `Given slow_down when classifyTokenError then interval is increased by 5`() {
        val result = classifyTokenError(httpCode = 400, error = "slow_down", errorDescription = null, currentInterval = 5)

        assertEquals(TokenPollResult.SlowDown(newInterval = 10), result)
    }

    @Test
    fun `Given access_denied when classifyTokenError then result is terminal Failed`() {
        val result = classifyTokenError(httpCode = 400, error = "access_denied", errorDescription = "使用者拒絕授權", currentInterval = 5)

        assertEquals(TokenPollResult.Failed("使用者拒絕授權"), result)
    }

    @Test
    fun `Given expired_token when classifyTokenError then result is terminal Failed`() {
        val result = classifyTokenError(httpCode = 400, error = "expired_token", errorDescription = null, currentInterval = 5)

        assertEquals(TokenPollResult.Failed("expired_token"), result)
    }

    @Test
    fun `Given an unknown OAuth error when classifyTokenError then result is terminal Failed`() {
        val result = classifyTokenError(httpCode = 400, error = "invalid_grant", errorDescription = null, currentInterval = 5)

        assertEquals(TokenPollResult.Failed("invalid_grant"), result)
    }

    @Test
    fun `Given a 4xx without a parseable error when classifyTokenError then result is terminal Failed`() {
        val result = classifyTokenError(httpCode = 400, error = null, errorDescription = null, currentInterval = 5)

        assertInstanceOf(TokenPollResult.Failed::class.java, result)
    }

    @Test
    fun `Given an IOException when classifyTokenException then result is TransientError`() {
        val result = classifyTokenException(IOException("connection reset"))

        assertInstanceOf(TokenPollResult.TransientError::class.java, result)
    }

    @Test
    fun `Given a SocketTimeoutException when classifyTokenException then result is TransientError`() {
        val result = classifyTokenException(SocketTimeoutException("read timed out"))

        assertInstanceOf(TokenPollResult.TransientError::class.java, result)
    }

    @Test
    fun `Given an UnknownHostException when classifyTokenException then result is TransientError`() {
        val result = classifyTokenException(UnknownHostException("no DNS"))

        assertInstanceOf(TokenPollResult.TransientError::class.java, result)
    }

    @Test
    fun `Given an unexpected exception when classifyTokenException then result is terminal Failed`() {
        val result = classifyTokenException(IllegalStateException("something odd"))

        assertInstanceOf(TokenPollResult.Failed::class.java, result)
    }

    @Test
    fun `Given a CancellationException when classifyTokenException then it is re-thrown not swallowed`() {
        assertThrows(CancellationException::class.java) {
            classifyTokenException(CancellationException("cancelled"))
        }
    }
}

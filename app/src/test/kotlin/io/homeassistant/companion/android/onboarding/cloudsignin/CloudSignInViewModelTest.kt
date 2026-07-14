package io.homeassistant.companion.android.onboarding.cloudsignin

import io.homeassistant.companion.android.onboarding.cloud.DeviceCodeResponse
import io.homeassistant.companion.android.onboarding.cloud.TokenPollResult
import io.homeassistant.companion.android.onboarding.cloud.TokenResponse
import io.homeassistant.companion.android.onboarding.cloud.WoowPaasApi
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class CloudSignInViewModelTest {

    private val api: WoowPaasApi = mockk()

    @Test
    fun `Given transient errors followed by success when polling then it keeps retrying until Authorized`() = runTest {
        coEvery { api.requestDeviceCode() } returns Result.success(deviceCodeResponse())
        coEvery { api.pollToken(any(), any()) } returnsMany listOf(
            TokenPollResult.TransientError("network blip"),
            TokenPollResult.TransientError("network blip"),
            TokenPollResult.Success(tokenResponse()),
        )
        val viewModel = CloudSignInViewModel(api, fixedClock())

        viewModel.startDeviceFlow()
        advanceUntilIdle()

        assertEquals(DeviceFlowUiState.Authorized("access-token"), viewModel.uiState.value)
        coVerify(atLeast = 3) { api.pollToken(any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["使用者拒絕授權", "expired_token"])
    fun `Given pollToken returns a terminal Failed when polling then it stops immediately with an Error`(reason: String) = runTest {
        coEvery { api.requestDeviceCode() } returns Result.success(deviceCodeResponse())
        coEvery { api.pollToken(any(), any()) } returns TokenPollResult.Failed(reason)
        val viewModel = CloudSignInViewModel(api, fixedClock())

        viewModel.startDeviceFlow()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertInstanceOf(DeviceFlowUiState.Error::class.java, state)
        assertEquals(reason, (state as DeviceFlowUiState.Error).message)
        coVerify(exactly = 1) { api.pollToken(any(), any()) }
    }

    @Test
    fun `Given a slow_down response when polling then it keeps polling and reaches Authorized`() = runTest {
        coEvery { api.requestDeviceCode() } returns Result.success(deviceCodeResponse(interval = 5))
        coEvery { api.pollToken(any(), any()) } returnsMany listOf(
            TokenPollResult.SlowDown(newInterval = 10),
            TokenPollResult.Success(tokenResponse()),
        )
        val viewModel = CloudSignInViewModel(api, fixedClock())

        viewModel.startDeviceFlow()
        advanceUntilIdle()

        assertEquals(DeviceFlowUiState.Authorized("access-token"), viewModel.uiState.value)
        coVerify(atLeast = 2) { api.pollToken(any(), any()) }
    }

    @Test
    fun `Given the network stays down when polling then it stops at the device code expiry with a friendly Error`() = runTest {
        val clock = FakeClock().apply { currentInstant = Instant.fromEpochMilliseconds(0) }
        coEvery { api.requestDeviceCode() } returns Result.success(deviceCodeResponse(expiresIn = 900, interval = 5))
        coEvery { api.pollToken(any(), any()) } returns TokenPollResult.TransientError("network down")
        val viewModel = CloudSignInViewModel(api, clock)

        viewModel.startDeviceFlow()
        runCurrent()

        // First poll hits a transient error while the device code is still valid: keep polling, show reconnecting.
        advanceTimeBy(5_500)
        runCurrent()
        val reconnecting = viewModel.uiState.value
        assertInstanceOf(DeviceFlowUiState.WaitingForAuth::class.java, reconnecting)
        assertTrue((reconnecting as DeviceFlowUiState.WaitingForAuth).isReconnecting)

        // The device code lifetime elapses while still offline: the loop must give up.
        clock.currentInstant = Instant.fromEpochMilliseconds(901_000)
        advanceUntilIdle()

        assertInstanceOf(DeviceFlowUiState.Error::class.java, viewModel.uiState.value)
        coVerify(atLeast = 2) { api.pollToken(any(), any()) }
    }

    @Test
    fun `Given a transient error then recovery when polling then the reconnecting flag clears`() = runTest {
        coEvery { api.requestDeviceCode() } returns Result.success(deviceCodeResponse(interval = 5))
        coEvery { api.pollToken(any(), any()) } returnsMany listOf(
            TokenPollResult.TransientError("blip"),
            TokenPollResult.Pending,
            TokenPollResult.Success(tokenResponse()),
        )
        val viewModel = CloudSignInViewModel(api, fixedClock())

        viewModel.startDeviceFlow()
        runCurrent()

        // First poll (after the 5s interval) fails transiently: the reconnecting hint turns on.
        advanceTimeBy(5_500)
        runCurrent()
        assertTrue((viewModel.uiState.value as DeviceFlowUiState.WaitingForAuth).isReconnecting)

        // Next poll (after the doubled 10s backoff) comes back as Pending: the hint clears.
        advanceTimeBy(10_500)
        runCurrent()
        val recovered = viewModel.uiState.value
        assertInstanceOf(DeviceFlowUiState.WaitingForAuth::class.java, recovered)
        assertFalse((recovered as DeviceFlowUiState.WaitingForAuth).isReconnecting)

        // Let the flow finish so no polling coroutine is left running.
        advanceUntilIdle()
        assertEquals(DeviceFlowUiState.Authorized("access-token"), viewModel.uiState.value)
    }

    @Test
    fun `Given a CancellationException during polling when polling then it propagates and does not become an Error`() = runTest {
        coEvery { api.requestDeviceCode() } returns Result.success(deviceCodeResponse())
        coEvery { api.pollToken(any(), any()) } throws CancellationException("cancelled during poll")
        val viewModel = CloudSignInViewModel(api, fixedClock())

        viewModel.startDeviceFlow()
        advanceUntilIdle()

        // A cancelled poll must not be swallowed into an Error state; the waiting screen stays put.
        assertInstanceOf(DeviceFlowUiState.WaitingForAuth::class.java, viewModel.uiState.value)
    }

    private fun fixedClock() = FakeClock()

    private fun deviceCodeResponse(expiresIn: Int = 900, interval: Int = 5) = DeviceCodeResponse(
        deviceCode = "device-code",
        userCode = "ABCD-1234",
        verificationUri = "https://stg.woowtech.io/device",
        verificationUriComplete = "https://stg.woowtech.io/device?user_code=ABCD-1234",
        expiresIn = expiresIn,
        interval = interval,
    )

    private fun tokenResponse() = TokenResponse(
        accessToken = "access-token",
        tokenType = "Bearer",
        expiresIn = 3600,
        scope = "ha:provision",
        refreshToken = null,
    )
}

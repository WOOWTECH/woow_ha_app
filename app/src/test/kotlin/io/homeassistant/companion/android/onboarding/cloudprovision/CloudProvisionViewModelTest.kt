package io.homeassistant.companion.android.onboarding.cloudprovision

import io.homeassistant.companion.android.onboarding.cloud.ApiException
import io.homeassistant.companion.android.onboarding.cloud.CloudOnboardingState
import io.homeassistant.companion.android.onboarding.cloud.ProvisionResponse
import io.homeassistant.companion.android.onboarding.cloud.StatusResponse
import io.homeassistant.companion.android.onboarding.cloud.WoowPaasApi
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val HA_URL = "https://ha.example.com"
private const val TEN_MINUTES_MS = 10 * 60 * 1000L

@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class CloudProvisionViewModelTest {

    private val api: WoowPaasApi = mockk()

    @Test
    fun `Given transient status failures then ready when provisioning then it keeps polling until Ready`() = runTest {
        coEvery { api.provision(any()) } returns Result.success(ProvisionResponse(status = "provisioning"))
        coEvery { api.getStatus(any()) } returnsMany listOf(
            Result.failure(IOException("blip")),
            Result.failure(SocketTimeoutException("timeout")),
            Result.success(StatusResponse(status = "ready", haUrl = HA_URL)),
        )
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertEquals(ProvisionUiState.Ready(HA_URL), viewModel.uiState.value)
        coVerify(atLeast = 3) { api.getStatus(any()) }
    }

    @Test
    fun `Given getStatus returns a terminal ApiException when provisioning then it stops with an Error`() = runTest {
        coEvery { api.provision(any()) } returns Result.success(ProvisionResponse(status = "provisioning"))
        coEvery { api.getStatus(any()) } returns Result.failure(ApiException(401, "登入已過期"))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertInstanceOf(ProvisionUiState.Error::class.java, viewModel.uiState.value)
        coVerify(exactly = 1) { api.getStatus(any()) }
    }

    @Test
    fun `Given the network stays down when provisioning then it stops at the 10 minute timeout`() = runTest {
        val clock = FakeClock().apply { currentInstant = Instant.fromEpochMilliseconds(0) }
        coEvery { api.provision(any()) } returns Result.success(ProvisionResponse(status = "provisioning"))
        coEvery { api.getStatus(any()) } returns Result.failure(IOException("network down"))
        val viewModel = viewModel(clock)
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        runCurrent()

        // First status poll fails transiently while still inside the timeout budget: keep waiting.
        advanceTimeBy(5_500)
        runCurrent()
        assertInstanceOf(ProvisionUiState.Provisioning::class.java, viewModel.uiState.value)

        // Ten minutes elapse while still offline: the poll loop must give up.
        clock.currentInstant = Instant.fromEpochMilliseconds(TEN_MINUTES_MS + 1_000)
        advanceUntilIdle()

        assertInstanceOf(ProvisionUiState.Error::class.java, viewModel.uiState.value)
    }

    @Test
    fun `Given status becomes error when provisioning then it stops with the reported error`() = runTest {
        coEvery { api.provision(any()) } returns Result.success(ProvisionResponse(status = "provisioning"))
        coEvery { api.getStatus(any()) } returns Result.success(StatusResponse(status = "error", error = "佈建失敗"))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertInstanceOf(ProvisionUiState.Error::class.java, state)
        assertEquals("佈建失敗", (state as ProvisionUiState.Error).message)
    }

    @Test
    fun `Given status ready without a url when provisioning then it stops with an Error`() = runTest {
        coEvery { api.provision(any()) } returns Result.success(ProvisionResponse(status = "provisioning"))
        coEvery { api.getStatus(any()) } returns Result.success(StatusResponse(status = "ready", haUrl = null))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertInstanceOf(ProvisionUiState.Error::class.java, viewModel.uiState.value)
    }

    @Test
    fun `Given status provisioning then ready when provisioning then it reaches Ready`() = runTest {
        coEvery { api.provision(any()) } returns Result.success(ProvisionResponse(status = "provisioning"))
        coEvery { api.getStatus(any()) } returnsMany listOf(
            Result.success(StatusResponse(status = "provisioning")),
            Result.success(StatusResponse(status = "ready", haUrl = HA_URL)),
        )
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertEquals(ProvisionUiState.Ready(HA_URL), viewModel.uiState.value)
    }

    @Test
    fun `Given provision returns ready immediately when provisioning then it goes straight to Ready without polling`() = runTest {
        coEvery { api.provision(any()) } returns Result.success(ProvisionResponse(status = "ready", haUrl = HA_URL))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertEquals(ProvisionUiState.Ready(HA_URL), viewModel.uiState.value)
        coVerify(exactly = 0) { api.getStatus(any()) }
    }

    @Test
    fun `Given getStatus throws a CancellationException when provisioning then it propagates and does not become an Error`() = runTest {
        coEvery { api.provision(any()) } returns Result.success(ProvisionResponse(status = "provisioning"))
        coEvery { api.getStatus(any()) } throws CancellationException("cancelled during poll")
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertInstanceOf(ProvisionUiState.Provisioning::class.java, viewModel.uiState.value)
    }

    @Test
    fun `Given getStatus yields a wrapped CancellationException when provisioning then it is not swallowed into an Error`() = runTest {
        coEvery { api.provision(any()) } returns Result.success(ProvisionResponse(status = "provisioning"))
        coEvery { api.getStatus(any()) } returns Result.failure(CancellationException("cancelled during poll"))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertInstanceOf(ProvisionUiState.Provisioning::class.java, viewModel.uiState.value)
    }

    private fun viewModel(clock: Clock = FakeClock()) = CloudProvisionViewModel(api, clock)

    private fun stateWithToken() = CloudOnboardingState().apply { accessToken = "token-123" }
}

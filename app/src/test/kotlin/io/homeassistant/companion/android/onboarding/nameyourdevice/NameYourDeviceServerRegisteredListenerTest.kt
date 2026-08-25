package io.homeassistant.companion.android.onboarding.nameyourdevice

import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.ServerRegistrationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.TemporaryServer
import io.homeassistant.companion.android.onboarding.ServerRegisteredListener
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.NameYourDeviceRoute
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Pins the [ServerRegisteredListener] seam in [NameYourDeviceViewModel] - the contract the cloud
 * edition's session cleanup relies on. These replace the direct-injection tests the cloud
 * repository had before the seam existed, asserting the same three semantics:
 * listeners run once after a successful registration, never when registration fails, and a
 * listener failure still rolls the registration back because the call sits inside the
 * registration try-block.
 */
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NameYourDeviceServerRegisteredListenerTest {

    private val route: NameYourDeviceRoute = NameYourDeviceRoute("http://ha.local", "test_auth_code")
    private val serverManager: ServerManager = mockk()
    private val serverRegistrationRepository: ServerRegistrationRepository = mockk()
    private val authenticationRepository: AuthenticationRepository = mockk()
    private val integrationRepository: IntegrationRepository = mockk()
    private val listener: ServerRegisteredListener = mockk()

    private val appVersionProvider = AppVersionProvider { AppVersion.from("test", 42) }
    private val messagingTokenProvider = MessagingTokenProvider { MessagingToken("test_messaging_token") }

    private fun createViewModel() = NameYourDeviceViewModel(
        route,
        serverManager,
        serverRegistrationRepository,
        appVersionProvider,
        messagingTokenProvider,
        serverRegisteredListeners = setOf(listener),
        defaultName = "Pixel 42",
    )

    private fun givenRegistrationSucceeds(serverId: Int = 1) {
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = route.url,
                authorizationCode = route.authCode,
                allowInsecureConnection = null,
            )
        } returns TemporaryServer(
            externalUrl = route.url,
            allowInsecureConnection = null,
            session = ServerSessionInfo(),
        )
        coEvery { serverManager.addServer(any()) } returns serverId
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.registerDevice(any()) } just Runs
        coEvery { serverManager.activateServer(serverId) } just Runs
        coEvery { serverManager.getServer(any<Int>()) } returns null
    }

    @Test
    fun `Given successful registration when onSaveClick then listener runs once after activation`() = runTest {
        givenRegistrationSucceeds(serverId = 1)
        coEvery { listener.onServerRegistered() } just Runs
        val viewModel = createViewModel()

        viewModel.onSaveClick()
        advanceUntilIdle()

        coVerifyOrder {
            serverManager.activateServer(1)
            listener.onServerRegistered()
        }
        coVerify(exactly = 1) { listener.onServerRegistered() }
    }

    @Test
    fun `Given registration fails when onSaveClick then listener never runs`() = runTest {
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = route.url,
                authorizationCode = route.authCode,
                allowInsecureConnection = null,
            )
        } returns null
        val viewModel = createViewModel()

        viewModel.onSaveClick()
        advanceUntilIdle()

        // The cloud session must survive a failed registration: the user may simply retry.
        coVerify(exactly = 0) { listener.onServerRegistered() }
    }

    @Test
    fun `Given a listener failure when onSaveClick then the registration is rolled back`() = runTest {
        givenRegistrationSucceeds(serverId = 7)
        coEvery { listener.onServerRegistered() } throws IllegalStateException("listener broke")
        coEvery { serverManager.authenticationRepository(7) } returns authenticationRepository
        coEvery { authenticationRepository.revokeSession() } just Runs
        coEvery { serverManager.removeServer(7) } just Runs
        val viewModel = createViewModel()

        viewModel.onSaveClick()
        advanceUntilIdle()

        // The listener call sits inside the registration try-block on purpose: an unexpected
        // failure there is treated like any other fatal registration error.
        coVerify(exactly = 1) { authenticationRepository.revokeSession() }
        coVerify(exactly = 1) { serverManager.removeServer(7) }
    }
}

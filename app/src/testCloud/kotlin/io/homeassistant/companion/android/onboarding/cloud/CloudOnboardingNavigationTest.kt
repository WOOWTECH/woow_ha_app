package io.homeassistant.companion.android.onboarding.cloud

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasApiConfig
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.onboarding.cloudchooser.navigation.CloudChooserRoute
import io.homeassistant.companion.android.onboarding.cloudprovision.navigation.CloudProvisionRoute
import io.homeassistant.companion.android.onboarding.cloudprovision.navigation.navigateToCloudProvisionAfterSignIn
import io.homeassistant.companion.android.onboarding.cloudsignin.navigation.CloudSignInRoute
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import junit.framework.TestCase.assertTrue
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CLOUD_OPTION_TITLE = "使用雲端服務"

/** The label the sign in screen only shows once a device flow attempt finished and failed. */
private const val SIGN_IN_RETRY_LABEL = "重新開始"

private const val DEVICE_FLOW_TIMEOUT_MS = 10_000L

/**
 * Stands in for the WOOW PaaS backend by accepting every connection and closing it immediately.
 *
 * The cloud screens talk to the real repository, which Hilt binds inside `:common` where no test in this
 * module can replace it, so what gets substituted is the endpoint it points at. Never answering keeps the
 * device flow away from a real network while making every attempt observable: [connectionCount] is how a
 * test tells that a screen started a device flow of its own rather than showing the outcome of an earlier
 * one.
 */
private class UnresponsiveBackend : AutoCloseable {

    private val serverSocket = ServerSocket(0, 0, InetAddress.getLoopbackAddress())
    private val connections = AtomicInteger()

    init {
        thread(isDaemon = true, name = "unresponsive-woow-paas-backend") {
            while (!serverSocket.isClosed) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: IOException) {
                    // The socket was closed while waiting for a connection: the test is over.
                    return@thread
                }
                connections.incrementAndGet()
                socket.close()
            }
        }
    }

    val url: String get() = "http://${serverSocket.inetAddress.hostAddress}:${serverSocket.localPort}"

    val connectionCount: Int get() = connections.get()

    override fun close() {
        serverSocket.close()
    }
}

/**
 * Navigation tests for the cloud onboarding flow.
 *
 * They pin down the shape of the back stack rather than the device flow itself. The screen that runs the
 * device flow reacts to being authorized by navigating forward, so leaving it on the stack turns the back
 * button into a trap: the user backs out of provisioning, lands on it, and is sent straight back. That
 * matters most exactly when the way out is the only thing left, namely when the stored session expired and
 * the provisioning screen offers no retry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(WoowPaasConfigModule::class)
@HiltAndroidTest
internal class CloudOnboardingNavigationTest : BaseOnboardingNavigationTest() {

    private val backend = UnresponsiveBackend()

    @BindValue
    @JvmField
    val woowPaasApiConfig: WoowPaasApiConfig = WoowPaasApiConfig(
        baseUrl = backend.url,
        clientId = "woow-ha-app",
        scopes = "ha:provision",
        deviceCodeGrantType = "urn:ietf:params:oauth:grant-type:device_code",
        refreshTokenGrantType = "refresh_token",
    )

    @After
    fun stopBackend() {
        backend.close()
    }

    @Test
    fun `Given the device was authorized when leaving provisioning then it goes back to the connection choice`() {
        testNavigation {
            onNodeWithText(CLOUD_OPTION_TITLE).assertIsDisplayed().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudSignInRoute>() == true)

            authorizeDevice()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudProvisionRoute>() == true)
            // What sits under provisioning is the connection choice, not the sign in screen: the screen that
            // would bounce the user forward again is no longer on the stack.
            assertTrue(navController.previousBackStackEntry?.destination?.hasRoute<CloudChooserRoute>() == true)

            goBack()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudChooserRoute>() == true)
            onNodeWithText(CLOUD_OPTION_TITLE).assertIsDisplayed()
        }
    }

    @Test
    fun `Given provisioning was left when choosing the cloud again then a new device flow starts`() {
        testNavigation {
            onNodeWithText(CLOUD_OPTION_TITLE).assertIsDisplayed().performClick()
            waitForDeviceFlowAttempt()
            val attemptsBeforeReturning = backend.connectionCount

            authorizeDevice()
            goBack()

            onNodeWithText(CLOUD_OPTION_TITLE).assertIsDisplayed().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudSignInRoute>() == true)
            // A brand new sign in screen: the previous entry was dropped from the stack, so its view model
            // went with it and the flow starts over instead of replaying an authorization already spent.
            waitUntil(DEVICE_FLOW_TIMEOUT_MS) { backend.connectionCount > attemptsBeforeReturning }
        }
    }

    /** Runs the step the sign in screen performs once the device flow authorized the device. */
    private fun AndroidComposeTestRule<*, *>.authorizeDevice() {
        runOnUiThread { navController.navigateToCloudProvisionAfterSignIn() }
        waitForIdle()
    }

    private fun AndroidComposeTestRule<*, *>.goBack() {
        runOnUiThread { navController.popBackStack() }
        waitForIdle()
    }

    /** Waits until the sign in screen finished an attempt, so a later count cannot include a lingering one. */
    private fun AndroidComposeTestRule<*, *>.waitForDeviceFlowAttempt() {
        waitUntil(DEVICE_FLOW_TIMEOUT_MS) {
            onAllNodesWithText(SIGN_IN_RETRY_LABEL).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

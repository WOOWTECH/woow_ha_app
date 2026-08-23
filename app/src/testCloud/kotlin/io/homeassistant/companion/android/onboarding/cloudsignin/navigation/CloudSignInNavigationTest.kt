package io.homeassistant.companion.android.onboarding.cloudsignin.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.onboarding.cloudchooser.navigation.CloudChooserRoute
import io.homeassistant.companion.android.onboarding.cloudprovision.navigation.CloudProvisionRoute
import io.homeassistant.companion.android.onboarding.cloudprovision.navigation.navigateToCloudProvisionAfterSignIn
import io.homeassistant.companion.android.onboarding.cloudsignin.CloudSignInViewModel
import io.homeassistant.companion.android.onboarding.cloudsignin.DeviceFlowUiState
import io.homeassistant.companion.android.testing.unit.stringResource
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val USER_CODE = "ABCD-1234"

/**
 * Navigation tests for the CloudSignIn screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class CloudSignInNavigationTest : BaseOnboardingNavigationTest() {

    // Kept as WaitingForAuth (not Authorized) so re-entering this screen never re-triggers the
    // Authorized -> onAuthorized() effect, which would otherwise immediately navigate forward again.
    private val uiStateFlow = MutableStateFlow<DeviceFlowUiState>(
        DeviceFlowUiState.WaitingForAuth(
            userCode = USER_CODE,
            verificationUri = "https://stg.woowtech.io/device",
            verificationUriComplete = "https://stg.woowtech.io/device?user_code=$USER_CODE",
        ),
    )

    @BindValue
    @JvmField
    val cloudSignInViewModel: CloudSignInViewModel = mockk(relaxed = true) {
        every { uiState } returns uiStateFlow
    }

    @Test
    fun `Given CloudSignIn when clicking back then show CloudChooser`() {
        testNavigation {
            navController.navigateToCloudSignIn()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudSignInRoute>() == true)

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudChooserRoute>() == true)
        }
    }

    @Test
    fun `Given clicking cloud card when authorized then show CloudProvision`() {
        testNavigation {
            onNodeWithText("使用雲端服務").performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudSignInRoute>() == true)
            onNodeWithText(USER_CODE).assertIsDisplayed()

            uiStateFlow.value = DeviceFlowUiState.Authorized
            waitForIdle()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudProvisionRoute>() == true)
        }
    }

    @Test
    fun `Given CloudProvision reached from CloudSignIn when pressing back then back stack unwinds directly to CloudChooser`() {
        testNavigation {
            navController.navigateToCloudSignIn()
            waitForIdle()

            // Mirrors the real onAuthorized() wiring, which pops CloudSignIn off the back stack (inclusive)
            // instead of pushing on top of it: nothing is left of CloudSignIn to unwind through once
            // CloudProvision is reached, so a single back press lands directly on CloudChooser. The back
            // stack this produces through the actual device flow is covered end to end by
            // CloudOnboardingNavigationTest; this test only pins down that CloudSignIn's own navigation
            // graph wires the same popUpTo.
            navController.navigateToCloudProvisionAfterSignIn()
            waitForIdle()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudProvisionRoute>() == true)

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
            waitForIdle()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudChooserRoute>() == true)
        }
    }
}

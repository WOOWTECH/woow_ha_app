package io.homeassistant.companion.android.onboarding.cloudprovision.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
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
import io.homeassistant.companion.android.onboarding.cloudprovision.CloudProvisionViewModel
import io.homeassistant.companion.android.onboarding.cloudprovision.ProvisionUiState
import io.homeassistant.companion.android.testing.unit.stringResource
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the CloudProvision screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class CloudProvisionNavigationTest : BaseOnboardingNavigationTest() {

    private val uiStateFlow = MutableStateFlow<ProvisionUiState>(ProvisionUiState.Idle)

    @BindValue
    @JvmField
    val cloudProvisionViewModel: CloudProvisionViewModel = mockk(relaxed = true) {
        every { uiState } returns uiStateFlow
    }

    @Test
    fun `Given CloudProvision when idle then shows provision button and back icon`() {
        testNavigation {
            navController.navigateToCloudProvision()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudProvisionRoute>() == true)

            onNodeWithText("開啟 Woow HA 服務").assertIsDisplayed()
            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given CloudProvision when clicking back icon then returns to CloudChooser`() {
        testNavigation {
            navController.navigateToCloudProvision()

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudChooserRoute>() == true)
        }
    }

    @Test
    fun `Given CloudProvision when provisioning then back icon is hidden`() {
        testNavigation {
            navController.navigateToCloudProvision()

            uiStateFlow.value = ProvisionUiState.Provisioning
            waitForIdle()

            onNodeWithText("正在開通您的 Woow HA 服務...").assertIsDisplayed()
            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsNotDisplayed()
        }
    }
}

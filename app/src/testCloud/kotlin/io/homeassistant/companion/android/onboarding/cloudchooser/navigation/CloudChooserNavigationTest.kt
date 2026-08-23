package io.homeassistant.companion.android.onboarding.cloudchooser.navigation

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.onboarding.cloudsignin.navigation.CloudSignInRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the CloudChooser screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class CloudChooserNavigationTest : BaseOnboardingNavigationTest() {

    @Test
    fun `Given no action when starting the app then show CloudChooser`() {
        testNavigation {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudChooserRoute>() == true)
        }
    }

    @Test
    fun `Given CloudChooser when clicking local card then show Welcome`() {
        testNavigation {
            onNodeWithText("連結本地設備").performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given CloudChooser when clicking cloud card then show CloudSignIn`() {
        testNavigation {
            onNodeWithText("使用雲端服務").performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<CloudSignInRoute>() == true)
        }
    }
}

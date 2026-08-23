package io.homeassistant.companion.android.onboarding.cloudchooser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class CloudChooserScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given CloudChooserScreen when displayed then both cards are shown`() {
        composeTestRule.setContent {
            CloudChooserScreen(onLocalClick = {}, onCloudClick = {})
        }

        composeTestRule.onNodeWithText("連結本地設備").assertIsDisplayed()
        composeTestRule.onNodeWithText("使用雲端服務").assertIsDisplayed()
    }

    @Test
    fun `Given CloudChooserScreen when clicking local card then triggers onLocalClick only`() {
        var localClicked = false
        var cloudClicked = false
        composeTestRule.setContent {
            CloudChooserScreen(
                onLocalClick = { localClicked = true },
                onCloudClick = { cloudClicked = true },
            )
        }

        composeTestRule.onNodeWithText("連結本地設備").performClick()

        assertTrue(localClicked)
        assertFalse(cloudClicked)
    }

    @Test
    fun `Given CloudChooserScreen when clicking cloud card then triggers onCloudClick only`() {
        var localClicked = false
        var cloudClicked = false
        composeTestRule.setContent {
            CloudChooserScreen(
                onLocalClick = { localClicked = true },
                onCloudClick = { cloudClicked = true },
            )
        }

        composeTestRule.onNodeWithText("使用雲端服務").performClick()

        assertTrue(cloudClicked)
        assertFalse(localClicked)
    }
}

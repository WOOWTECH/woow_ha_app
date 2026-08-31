package io.homeassistant.companion.android.onboarding.cloudprovision

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
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
class CloudProvisionScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given Idle state when clicking provision button then triggers onProvisionClick`() {
        var provisionClicked = false
        composeTestRule.setContent {
            CloudProvisionContent(
                uiState = ProvisionUiState.Idle,
                onBackClick = {},
                onProvisionClick = { provisionClicked = true },
            )
        }

        composeTestRule.onNodeWithText("開啟 Woow HA 服務").performClick()

        assertTrue(provisionClicked)
    }

    @Test
    fun `Given Idle state when clicking back icon then triggers onBackClick`() {
        var backClicked = false
        composeTestRule.setContent {
            CloudProvisionContent(
                uiState = ProvisionUiState.Idle,
                onBackClick = { backClicked = true },
                onProvisionClick = {},
            )
        }

        composeTestRule.onNodeWithContentDescription(
            composeTestRule.stringResource(commonR.string.navigate_up),
        ).performClick()

        assertTrue(backClicked)
    }

    @Test
    fun `Given Provisioning state when displayed then shows loading text and no back icon`() {
        composeTestRule.setContent {
            CloudProvisionContent(
                uiState = ProvisionUiState.Provisioning,
                onBackClick = {},
                onProvisionClick = {},
            )
        }

        composeTestRule.onNodeWithText("正在開通您的 Woow HA 服務...").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.stringResource(commonR.string.navigate_up),
        ).assertIsNotDisplayed()
    }

    @Test
    fun `Given Ready state when displayed then shows success text and no back icon`() {
        composeTestRule.setContent {
            CloudProvisionContent(
                uiState = ProvisionUiState.Ready(serverUrl = "https://demo.ha-slim.woowtech.io"),
                onBackClick = {},
                onProvisionClick = {},
            )
        }

        composeTestRule.onNodeWithText("開通完成！正在連線...").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.stringResource(commonR.string.navigate_up),
        ).assertIsNotDisplayed()
    }

    @Test
    fun `Given Error state with canRetry when clicking retry then triggers onProvisionClick not onBackClick`() {
        var provisionClicked = false
        var backClicked = false
        composeTestRule.setContent {
            CloudProvisionContent(
                uiState = ProvisionUiState.Error(message = "服務尚未開放，請稍後再試", canRetry = true),
                onBackClick = { backClicked = true },
                onProvisionClick = { provisionClicked = true },
            )
        }

        composeTestRule.onNodeWithText("服務尚未開放，請稍後再試").assertIsDisplayed()
        composeTestRule.onNodeWithText("重試").performClick()

        assertTrue(provisionClicked)
        assertFalse(backClicked)
    }

    @Test
    fun `Given Error state with canRetry when clicking bottom back button then triggers onBackClick not onProvisionClick`() {
        var provisionClicked = false
        var backClicked = false
        composeTestRule.setContent {
            CloudProvisionContent(
                uiState = ProvisionUiState.Error(message = "服務尚未開放，請稍後再試", canRetry = true),
                onBackClick = { backClicked = true },
                onProvisionClick = { provisionClicked = true },
            )
        }

        composeTestRule.onNodeWithText("返回").performClick()

        assertTrue(backClicked)
        assertFalse(provisionClicked)
    }

    @Test
    fun `Given Error state without canRetry when displayed then no retry button but back button and back icon are shown`() {
        var backClicked = false
        composeTestRule.setContent {
            CloudProvisionContent(
                uiState = ProvisionUiState.Error(message = "登入已過期，請返回重新登入", canRetry = false),
                onBackClick = { backClicked = true },
                onProvisionClick = {},
            )
        }

        composeTestRule.onNodeWithText("登入已過期，請返回重新登入").assertIsDisplayed()
        composeTestRule.onNodeWithText("重試").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.stringResource(commonR.string.navigate_up),
        ).assertIsDisplayed()

        composeTestRule.onNodeWithText("返回").performClick()

        assertTrue(backClicked)
    }

    @Test
    fun `Given Suspended state when displayed then shows suspended text and no action buttons`() {
        composeTestRule.setContent {
            CloudProvisionContent(
                uiState = ProvisionUiState.Suspended,
                onBackClick = {},
                onProvisionClick = {},
            )
        }

        composeTestRule.onNodeWithText("服務已暫停").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.stringResource(commonR.string.navigate_up),
        ).assertIsNotDisplayed()
    }

    @Test
    fun `Given Deleting state when clicking retry then triggers onProvisionClick`() {
        var provisionClicked = false
        composeTestRule.setContent {
            CloudProvisionContent(
                uiState = ProvisionUiState.Deleting,
                onBackClick = {},
                onProvisionClick = { provisionClicked = true },
            )
        }

        composeTestRule.onNodeWithText("前一個服務正在刪除中").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.stringResource(commonR.string.navigate_up),
        ).assertIsNotDisplayed()
        composeTestRule.onNodeWithText("重試").performClick()

        assertTrue(provisionClicked)
    }
}

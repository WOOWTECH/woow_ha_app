package io.homeassistant.companion.android.onboarding.cloudsignin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class CloudSignInScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given RequestingCode state when displayed then shows loading text`() {
        composeTestRule.setContent {
            CloudSignInContent(
                uiState = DeviceFlowUiState.RequestingCode,
                onBackClick = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("正在準備驗證...").assertIsDisplayed()
    }

    @Test
    fun `Given WaitingForAuth state when displayed then shows user code and waiting hint`() {
        composeTestRule.setContent {
            CloudSignInContent(
                uiState = DeviceFlowUiState.WaitingForAuth(
                    userCode = "ABCD-1234",
                    verificationUri = "https://stg.woowtech.io/device",
                    verificationUriComplete = "https://stg.woowtech.io/device?user_code=ABCD-1234",
                ),
                onBackClick = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("ABCD-1234").assertIsDisplayed()
        composeTestRule.onNodeWithText("等待瀏覽器授權中...").assertIsDisplayed()
        composeTestRule.onNodeWithText("前往驗證").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Given WaitingForAuth reconnecting when displayed then shows reconnecting hint instead of waiting hint`() {
        composeTestRule.setContent {
            CloudSignInContent(
                uiState = DeviceFlowUiState.WaitingForAuth(
                    userCode = "ABCD-1234",
                    verificationUri = "https://stg.woowtech.io/device",
                    verificationUriComplete = "https://stg.woowtech.io/device?user_code=ABCD-1234",
                    isReconnecting = true,
                ),
                onBackClick = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("連線中斷，重新連線中…").assertIsDisplayed()
        composeTestRule.onNodeWithText("等待瀏覽器授權中...").assertIsNotDisplayed()
    }

    @Test
    fun `Given Authorized state when displayed then shows success text`() {
        composeTestRule.setContent {
            CloudSignInContent(
                uiState = DeviceFlowUiState.Authorized,
                onBackClick = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("授權成功！正在前往開通...").assertIsDisplayed()
    }

    @Test
    fun `Given Error state with canRetry when clicking retry then triggers onRetry`() {
        var retried = false
        composeTestRule.setContent {
            CloudSignInContent(
                uiState = DeviceFlowUiState.Error(message = "無法取得驗證碼", canRetry = true),
                onBackClick = {},
                onRetry = { retried = true },
            )
        }

        composeTestRule.onNodeWithText("無法取得驗證碼").assertIsDisplayed()
        composeTestRule.onNodeWithText("重新開始").performClick()

        assertTrue(retried)
    }

    @Test
    fun `Given Error state without canRetry when displayed then no retry button is shown`() {
        composeTestRule.setContent {
            CloudSignInContent(
                uiState = DeviceFlowUiState.Error(message = "連線逾時，請重新開始登入", canRetry = false),
                onBackClick = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("連線逾時，請重新開始登入").assertIsDisplayed()
        composeTestRule.onNodeWithText("重新開始").assertIsNotDisplayed()
    }

    @Test
    fun `Given any state when clicking back then triggers onBackClick`() {
        var backClicked = false
        var retried = false
        composeTestRule.setContent {
            CloudSignInContent(
                uiState = DeviceFlowUiState.Error(message = "無法取得驗證碼", canRetry = true),
                onBackClick = { backClicked = true },
                onRetry = { retried = true },
            )
        }

        composeTestRule.onNodeWithContentDescription(
            composeTestRule.stringResource(commonR.string.navigate_up),
        ).performClick()

        assertTrue(backClicked)
        assertFalse(retried)
    }
}

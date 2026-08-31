package io.homeassistant.companion.android.onboarding.cloudsignin

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class CloudSignInScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudSignInScreen loading`() {
        HAThemeForPreview {
            CloudSignInContent(
                uiState = DeviceFlowUiState.RequestingCode,
                onBackClick = {},
                onRetry = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudSignInScreen waiting for auth`() {
        HAThemeForPreview {
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
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudSignInScreen waiting for auth reconnecting`() {
        HAThemeForPreview {
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
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudSignInScreen error with retry`() {
        HAThemeForPreview {
            CloudSignInContent(
                uiState = DeviceFlowUiState.Error(message = "無法取得驗證碼", canRetry = true),
                onBackClick = {},
                onRetry = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudSignInScreen error without retry`() {
        HAThemeForPreview {
            CloudSignInContent(
                uiState = DeviceFlowUiState.Error(message = "連線逾時，請重新開始登入", canRetry = false),
                onBackClick = {},
                onRetry = {},
            )
        }
    }
}

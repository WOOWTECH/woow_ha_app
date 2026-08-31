package io.homeassistant.companion.android.onboarding.cloudprovision

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class CloudProvisionScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudProvisionScreen idle`() {
        HAThemeForPreview {
            CloudProvisionContent(
                uiState = ProvisionUiState.Idle,
                onBackClick = {},
                onProvisionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudProvisionScreen provisioning`() {
        HAThemeForPreview {
            CloudProvisionContent(
                uiState = ProvisionUiState.Provisioning,
                onBackClick = {},
                onProvisionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudProvisionScreen ready`() {
        HAThemeForPreview {
            CloudProvisionContent(
                uiState = ProvisionUiState.Ready(serverUrl = "https://demo.ha-slim.woowtech.io"),
                onBackClick = {},
                onProvisionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudProvisionScreen error with retry`() {
        HAThemeForPreview {
            CloudProvisionContent(
                uiState = ProvisionUiState.Error(message = "服務尚未開放，請稍後再試", canRetry = true),
                onBackClick = {},
                onProvisionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudProvisionScreen error without retry`() {
        HAThemeForPreview {
            CloudProvisionContent(
                uiState = ProvisionUiState.Error(message = "登入已過期，請返回重新登入", canRetry = false),
                onBackClick = {},
                onProvisionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudProvisionScreen suspended`() {
        HAThemeForPreview {
            CloudProvisionContent(
                uiState = ProvisionUiState.Suspended,
                onBackClick = {},
                onProvisionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudProvisionScreen deleting`() {
        HAThemeForPreview {
            CloudProvisionContent(
                uiState = ProvisionUiState.Deleting,
                onBackClick = {},
                onProvisionClick = {},
            )
        }
    }
}

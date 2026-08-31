package io.homeassistant.companion.android.onboarding.cloudchooser

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class CloudChooserScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `CloudChooserScreen default`() {
        HAThemeForPreview {
            CloudChooserScreen(onLocalClick = {}, onCloudClick = {})
        }
    }
}

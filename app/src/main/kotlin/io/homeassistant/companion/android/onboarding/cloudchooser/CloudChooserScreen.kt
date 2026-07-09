package io.homeassistant.companion.android.onboarding.cloudchooser

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.compose.HAPreviews

private val ICON_SIZE = 120.dp

@Composable
internal fun CloudChooserScreen(
    onLocalClick: () -> Unit,
    onCloudClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        val positionPercentage = 0.15f
        Spacer(modifier = Modifier.weight(positionPercentage))

        Image(
            painter = painterResource(R.drawable.ic_woowtech_branding),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
        )

        Text(
            text = "選擇連線方式",
            style = HATextStyle.Headline,
        )

        Text(
            text = "請選擇您要如何連接 Home Assistant",
            style = HATextStyle.Body,
        )

        Spacer(modifier = Modifier.weight(0.1f))

        ChooserCard(
            icon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(32.dp)) },
            title = "連結本地設備",
            subtitle = "連接您已架設好的 Home Assistant",
            onClick = onLocalClick,
        )

        ChooserCard(
            icon = { Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(32.dp)) },
            title = "使用雲端服務",
            subtitle = "立即開通雲端 Woow HA",
            onClick = onCloudClick,
        )

        Spacer(modifier = Modifier.weight(1f - positionPercentage))
    }
}

@Composable
private fun ChooserCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = LocalHAColorScheme.current.colorSurfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(HADimens.SPACE4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            icon()
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = HATextStyle.BodyMedium)
                Text(
                    text = subtitle,
                    style = HATextStyle.Body,
                    color = LocalHAColorScheme.current.colorOnNeutralNormal,
                )
            }
        }
    }
}

@HAPreviews
@Composable
private fun CloudChooserScreenPreview() {
    HAThemeForPreview {
        CloudChooserScreen(onLocalClick = {}, onCloudClick = {})
    }
}

package io.homeassistant.companion.android.onboarding.cloudprovision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.launch.startLaunchOnboarding
import io.homeassistant.companion.android.util.compose.HAPreviews

@Composable
internal fun CloudProvisionScreen(
    viewModel: CloudProvisionViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        if (uiState is ProvisionUiState.Ready) {
            context.startLaunchOnboarding(
                urlToOnboard = (uiState as ProvisionUiState.Ready).serverUrl,
                hideExistingServers = false,
                skipWelcome = true,
            )
        }
    }

    CloudProvisionContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onProvisionClick = viewModel::onProvisionClicked,
        modifier = modifier,
    )
}

@Composable
private fun CloudProvisionContent(
    uiState: ProvisionUiState,
    onBackClick: () -> Unit,
    onProvisionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (uiState is ProvisionUiState.Idle || uiState is ProvisionUiState.Error) {
                HATopBar(onBackClick = onBackClick)
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = HADimens.SPACE4)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            when (uiState) {
                is ProvisionUiState.Idle -> {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = LocalHAColorScheme.current.colorFillPrimaryNormalResting,
                    )
                    Text(
                        text = "開通雲端服務",
                        style = HATextStyle.Headline,
                    )
                    Text(
                        text = "點擊下方按鈕，為您開啟專屬的 Woow HA 伺服器",
                        style = HATextStyle.Body,
                    )
                }
                is ProvisionUiState.Provisioning -> {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Text(
                        text = "正在開通您的 Woow HA 服務...",
                        style = HATextStyle.Body,
                    )
                }
                is ProvisionUiState.Ready -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = LocalHAColorScheme.current.colorFillPrimaryNormalResting,
                    )
                    Text(
                        text = "開通完成！正在連線...",
                        style = HATextStyle.Headline,
                    )
                }
                is ProvisionUiState.Error -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = LocalHAColorScheme.current.colorFillDangerNormalResting,
                    )
                    Text(
                        text = uiState.message,
                        style = HATextStyle.Body,
                        textAlign = TextAlign.Center,
                    )
                }
                is ProvisionUiState.Suspended -> {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = LocalHAColorScheme.current.colorFillWarningNormalResting,
                    )
                    Text(
                        text = "服務已暫停",
                        style = HATextStyle.Headline,
                    )
                    Text(
                        text = "您的 Woow HA 服務已被暫停，請聯繫支援",
                        style = HATextStyle.Body,
                        textAlign = TextAlign.Center,
                    )
                }
                is ProvisionUiState.Deleting -> {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Text(
                        text = "前一個服務正在刪除中",
                        style = HATextStyle.Headline,
                    )
                    Text(
                        text = "請稍後重新開通",
                        style = HATextStyle.Body,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))

            when (uiState) {
                is ProvisionUiState.Idle -> {
                    HAAccentButton(
                        text = "開啟 Woow HA 服務",
                        onClick = onProvisionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = HADimens.SPACE6),
                    )
                }
                is ProvisionUiState.Error -> {
                    if (uiState.canRetry) {
                        HAAccentButton(
                            text = "重試",
                            onClick = onProvisionClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HAPlainButton(
                        text = "返回",
                        onClick = onBackClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = HADimens.SPACE6),
                    )
                }
                is ProvisionUiState.Deleting -> {
                    HAAccentButton(
                        text = "重試",
                        onClick = onProvisionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = HADimens.SPACE6),
                    )
                }
                else -> {}
            }
        }
    }
}

@HAPreviews
@Composable
private fun CloudProvisionScreenIdlePreview() {
    HAThemeForPreview {
        CloudProvisionContent(
            uiState = ProvisionUiState.Idle,
            onBackClick = {},
            onProvisionClick = {},
        )
    }
}

@HAPreviews
@Composable
private fun CloudProvisionScreenErrorPreview() {
    HAThemeForPreview {
        CloudProvisionContent(
            uiState = ProvisionUiState.Error(message = "服務尚未開放，請稍後再試", canRetry = true),
            onBackClick = {},
            onProvisionClick = {},
        )
    }
}

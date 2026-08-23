package io.homeassistant.companion.android.onboarding.cloudsignin

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.compose.HAPreviews

@Composable
internal fun CloudSignInScreen(
    viewModel: CloudSignInViewModel,
    onBackClick: () -> Unit,
    onAuthorized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (uiState is DeviceFlowUiState.Idle) {
            viewModel.startDeviceFlow()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is DeviceFlowUiState.Authorized && !hasNavigated) {
            hasNavigated = true
            onAuthorized()
        }
    }

    CloudSignInContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetry = viewModel::startDeviceFlow,
        modifier = modifier,
    )
}

@Composable
internal fun CloudSignInContent(
    uiState: DeviceFlowUiState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onBackClick = onBackClick) },
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
            Spacer(modifier = Modifier.weight(0.2f))

            when (uiState) {
                is DeviceFlowUiState.Idle,
                is DeviceFlowUiState.RequestingCode,
                -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        text = "正在準備驗證...",
                        style = HATextStyle.Body,
                    )
                }

                is DeviceFlowUiState.WaitingForAuth -> {
                    WaitingForAuthContent(
                        userCode = uiState.userCode,
                        verificationUriComplete = uiState.verificationUriComplete,
                        isReconnecting = uiState.isReconnecting,
                    )
                }

                is DeviceFlowUiState.Authorized -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        text = "授權成功！正在前往開通...",
                        style = HATextStyle.Body,
                    )
                }

                is DeviceFlowUiState.Error -> {
                    ErrorContent(
                        message = uiState.message,
                        canRetry = uiState.canRetry,
                        onRetry = onRetry,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.8f))
        }
    }
}

@Composable
private fun ColumnScope.WaitingForAuthContent(
    userCode: String,
    verificationUriComplete: String,
    isReconnecting: Boolean,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Text(
        text = "雲端登入",
        style = HATextStyle.Headline,
    )

    Text(
        text = "請在瀏覽器中輸入以下驗證碼完成登入",
        style = HATextStyle.Body,
        textAlign = TextAlign.Center,
    )

    Text(
        text = userCode,
        style = HATextStyle.Headline.copy(
            fontSize = 36.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp,
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = HADimens.SPACE4),
    )

    IconButton(
        onClick = { clipboardManager.setText(AnnotatedString(userCode)) },
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "複製驗證碼",
        )
    }

    CircularProgressIndicator(modifier = Modifier.size(32.dp))

    Text(
        text = if (isReconnecting) "連線中斷，重新連線中…" else "等待瀏覽器授權中...",
        style = HATextStyle.Body,
        color = LocalHAColorScheme.current.colorTextSecondary,
    )

    HAAccentButton(
        text = "前往驗證",
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, verificationUriComplete.toUri())
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.ErrorContent(message: String, canRetry: Boolean, onRetry: () -> Unit) {
    Icon(
        imageVector = Icons.Default.ErrorOutline,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = LocalHAColorScheme.current.colorFillDangerNormalResting,
    )

    Text(
        text = message,
        style = HATextStyle.Body,
        textAlign = TextAlign.Center,
    )

    if (canRetry) {
        HAPlainButton(
            text = "重新開始",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@HAPreviews
@Composable
private fun CloudSignInWaitingPreview() {
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

@HAPreviews
@Composable
private fun CloudSignInReconnectingPreview() {
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

@HAPreviews
@Composable
private fun CloudSignInErrorPreview() {
    HAThemeForPreview {
        CloudSignInContent(
            uiState = DeviceFlowUiState.Error(message = "無法取得驗證碼", canRetry = true),
            onBackClick = {},
            onRetry = {},
        )
    }
}

package io.homeassistant.companion.android.onboarding.cloudsignin

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

@Composable
internal fun CloudSignInScreen(
    viewModel: CloudSignInViewModel,
    onBackClick: () -> Unit,
    onSignInSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is SignInUiState.Success) {
            onSignInSuccess()
        }
    }

    CloudSignInContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onSignInClick = viewModel::onSignInClick,
        modifier = modifier,
    )
}

@Composable
private fun CloudSignInContent(
    uiState: SignInUiState,
    onBackClick: () -> Unit,
    onSignInClick: (email: String, password: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val isLoading = uiState is SignInUiState.Loading

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
            Text(
                text = "雲端登入",
                style = HATextStyle.Headline,
                modifier = Modifier.padding(top = HADimens.SPACE6),
            )

            Text(
                text = "使用您的 Woow 帳號登入或註冊",
                style = HATextStyle.Body,
            )

            HATextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Email", style = HATextStyle.UserInput) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )

            HATextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("密碼", style = HATextStyle.UserInput) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (email.isNotEmpty() && password.isNotEmpty() && !isLoading) {
                            onSignInClick(email, password)
                        }
                    },
                ),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }

            HAAccentButton(
                text = "登入 / 註冊",
                onClick = { onSignInClick(email, password) },
                enabled = email.isNotEmpty() && password.isNotEmpty() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = HADimens.SPACE6),
            )
        }
    }
}

@HAPreviews
@Composable
private fun CloudSignInScreenPreview() {
    HAThemeForPreview {
        CloudSignInContent(
            uiState = SignInUiState.Idle,
            onBackClick = {},
            onSignInClick = { _, _ -> },
        )
    }
}

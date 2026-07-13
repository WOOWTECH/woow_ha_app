package io.homeassistant.companion.android.onboarding.cloudsignin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.onboarding.cloud.TokenPollResult
import io.homeassistant.companion.android.onboarding.cloud.WoowPaasApi
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
internal class CloudSignInViewModel @Inject constructor(
    private val api: WoowPaasApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeviceFlowUiState>(DeviceFlowUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var deviceFlowJob: Job? = null
    private var currentDeviceCode: String? = null
    private var currentInterval: Int = 5

    fun startDeviceFlow() {
        deviceFlowJob?.cancel()
        pollJob?.cancel()
        deviceFlowJob = viewModelScope.launch {
            try {
                _uiState.value = DeviceFlowUiState.RequestingCode

                api.requestDeviceCode().fold(
                    onSuccess = { response ->
                        currentDeviceCode = response.deviceCode
                        currentInterval = response.interval
                        _uiState.value = DeviceFlowUiState.WaitingForAuth(
                            userCode = response.userCode,
                            verificationUri = response.verificationUri,
                            verificationUriComplete = response.verificationUriComplete,
                        )
                        startPolling()
                    },
                    onFailure = { error ->
                        _uiState.value = DeviceFlowUiState.Error(
                            message = error.message ?: "無法取得驗證碼",
                            canRetry = true,
                        )
                    },
                )
            } catch (e: Exception) {
                _uiState.value = DeviceFlowUiState.Error(
                    message = "連線失敗: ${e.message}",
                    canRetry = true,
                )
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val deviceCode = currentDeviceCode ?: return@launch

            while (true) {
                delay(currentInterval * 1000L)

                when (val result = api.pollToken(deviceCode, currentInterval)) {
                    is TokenPollResult.Success -> {
                        _uiState.value = DeviceFlowUiState.Authorized(
                            accessToken = result.token.accessToken,
                        )
                        return@launch
                    }
                    is TokenPollResult.Pending -> {
                        // Continue polling
                    }
                    is TokenPollResult.SlowDown -> {
                        currentInterval = result.newInterval
                    }
                    is TokenPollResult.Failed -> {
                        _uiState.value = DeviceFlowUiState.Error(
                            message = result.error,
                            canRetry = true,
                        )
                        return@launch
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}

internal sealed interface DeviceFlowUiState {
    data object Idle : DeviceFlowUiState
    data object RequestingCode : DeviceFlowUiState
    data class WaitingForAuth(
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String,
    ) : DeviceFlowUiState
    data class Authorized(val accessToken: String) : DeviceFlowUiState
    data class Error(val message: String, val canRetry: Boolean) : DeviceFlowUiState
}

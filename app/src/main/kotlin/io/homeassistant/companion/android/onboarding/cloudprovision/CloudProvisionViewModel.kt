package io.homeassistant.companion.android.onboarding.cloudprovision

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.onboarding.cloud.ApiException
import io.homeassistant.companion.android.onboarding.cloud.WoowPaasApi
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val INITIAL_POLL_INTERVAL_MS = 5_000L
private const val MAX_POLL_INTERVAL_MS = 30_000L
private const val POLL_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes

@HiltViewModel
internal class CloudProvisionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: WoowPaasApi,
) : ViewModel() {

    val accessToken: String = savedStateHandle.get<String>("accessToken")!!

    private val _uiState = MutableStateFlow<ProvisionUiState>(ProvisionUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var pollJob: Job? = null

    fun onProvisionClicked() {
        viewModelScope.launch {
            _uiState.value = ProvisionUiState.Provisioning

            api.provision(accessToken).fold(
                onSuccess = { response ->
                    when (response.status) {
                        "ready" -> {
                            _uiState.value = ProvisionUiState.Ready(response.haUrl!!)
                        }
                        "provisioning" -> {
                            startStatusPolling()
                        }
                        "suspended" -> {
                            _uiState.value = ProvisionUiState.Suspended
                        }
                        "deleting" -> {
                            _uiState.value = ProvisionUiState.Deleting
                        }
                        else -> {
                            _uiState.value = ProvisionUiState.Error(
                                message = "未預期的狀態: ${response.status}",
                                canRetry = true,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    val message = when {
                        error is ApiException && error.code == 503 ->
                            "服務尚未開放，請稍後再試"
                        error is ApiException && error.code == 401 ->
                            "登入已過期，請返回重新登入"
                        error is ApiException && error.code == 403 ->
                            "權限不足（缺少 ha:provision scope）"
                        else -> error.message ?: "開通失敗"
                    }
                    _uiState.value = ProvisionUiState.Error(
                        message = message,
                        canRetry = error !is ApiException || error.code != 401,
                    )
                },
            )
        }
    }

    private fun startStatusPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var interval = INITIAL_POLL_INTERVAL_MS
            val startTime = System.currentTimeMillis()

            while (true) {
                delay(interval)

                if (System.currentTimeMillis() - startTime > POLL_TIMEOUT_MS) {
                    _uiState.value = ProvisionUiState.Error(
                        message = "開通逾時（超過 10 分鐘），請重試或聯繫支援",
                        canRetry = true,
                    )
                    return@launch
                }

                api.getStatus(accessToken).fold(
                    onSuccess = { response ->
                        when (response.status) {
                            "ready" -> {
                                _uiState.value = ProvisionUiState.Ready(response.haUrl!!)
                                return@launch
                            }
                            "provisioning" -> {
                                // Continue polling with exponential backoff
                                interval = (interval * 2).coerceAtMost(MAX_POLL_INTERVAL_MS)
                            }
                            "error" -> {
                                _uiState.value = ProvisionUiState.Error(
                                    message = response.error ?: "佈建失敗",
                                    canRetry = true,
                                )
                                return@launch
                            }
                            "suspended" -> {
                                _uiState.value = ProvisionUiState.Suspended
                                return@launch
                            }
                            "deleting" -> {
                                _uiState.value = ProvisionUiState.Deleting
                                return@launch
                            }
                            "none" -> {
                                _uiState.value = ProvisionUiState.Error(
                                    message = "異常狀態：尚未開通",
                                    canRetry = true,
                                )
                                return@launch
                            }
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = ProvisionUiState.Error(
                            message = error.message ?: "查詢狀態失敗",
                            canRetry = true,
                        )
                        return@launch
                    },
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}

internal sealed interface ProvisionUiState {
    data object Idle : ProvisionUiState
    data object Provisioning : ProvisionUiState
    data class Ready(val serverUrl: String) : ProvisionUiState
    data class Error(val message: String, val canRetry: Boolean) : ProvisionUiState
    data object Suspended : ProvisionUiState
    data object Deleting : ProvisionUiState
}

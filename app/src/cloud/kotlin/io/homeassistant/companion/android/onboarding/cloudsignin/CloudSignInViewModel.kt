package io.homeassistant.companion.android.onboarding.cloudsignin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.woowpaas.TokenPollResult
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasRepository
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_POLL_INTERVAL_SECONDS = 5
private const val MAX_POLL_BACKOFF_SECONDS = 60

@OptIn(ExperimentalTime::class)
@HiltViewModel
internal class CloudSignInViewModel @Inject constructor(
    private val repository: WoowPaasRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeviceFlowUiState>(DeviceFlowUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var deviceFlowJob: Job? = null
    private var currentDeviceCode: String? = null
    private var currentInterval: Int = DEFAULT_POLL_INTERVAL_SECONDS

    // The last "waiting" state, reused to toggle the reconnecting hint without losing the user code.
    private var waitingState: DeviceFlowUiState.WaitingForAuth? = null

    // Client-side upper bound: once the device code has expired we stop even if the server is unreachable.
    private var pollDeadline: Instant? = null

    fun startDeviceFlow() {
        deviceFlowJob?.cancel()
        pollJob?.cancel()
        deviceFlowJob = viewModelScope.launch {
            try {
                _uiState.value = DeviceFlowUiState.RequestingCode

                repository.requestDeviceCode().fold(
                    onSuccess = { response ->
                        currentDeviceCode = response.deviceCode
                        currentInterval = response.interval
                        pollDeadline = clock.now() + response.expiresIn.seconds
                        val waiting = DeviceFlowUiState.WaitingForAuth(
                            userCode = response.userCode,
                            verificationUri = response.verificationUri,
                            verificationUriComplete = response.verificationUriComplete,
                        )
                        waitingState = waiting
                        _uiState.value = waiting
                        startPolling()
                    },
                    onFailure = { error ->
                        _uiState.value = DeviceFlowUiState.Error(
                            message = error.message ?: "無法取得驗證碼",
                            canRetry = true,
                        )
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = DeviceFlowUiState.Error(
                    message = "連線失敗: ${e.message}",
                    canRetry = true,
                )
            }
        }
    }

    /**
     * Polls the token endpoint until the user authorizes, the device code expires or a terminal error occurs.
     *
     * Transient failures (network hiccups, HTTP 5xx) do not abort the flow: the loop keeps polling with an
     * exponential backoff and surfaces a "reconnecting" hint, bounded by the device code lifetime.
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val deviceCode = currentDeviceCode ?: return@launch
            var backoffInterval = currentInterval

            while (true) {
                delay(backoffInterval.seconds)

                when (val result = repository.pollToken(deviceCode, currentInterval)) {
                    is TokenPollResult.Success -> {
                        _uiState.value = DeviceFlowUiState.Authorized
                        return@launch
                    }
                    is TokenPollResult.Pending -> {
                        clearReconnecting()
                        backoffInterval = currentInterval
                    }
                    is TokenPollResult.SlowDown -> {
                        currentInterval = result.newInterval
                        backoffInterval = currentInterval
                        clearReconnecting()
                    }
                    is TokenPollResult.TransientError -> {
                        if (isDeviceCodeExpired()) {
                            _uiState.value = DeviceFlowUiState.Error(
                                message = "連線逾時，請重新開始登入",
                                canRetry = true,
                            )
                            return@launch
                        }
                        showReconnecting()
                        backoffInterval = (backoffInterval * 2).coerceAtMost(MAX_POLL_BACKOFF_SECONDS)
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

    private fun isDeviceCodeExpired(): Boolean {
        val deadline = pollDeadline ?: return false
        return clock.now() >= deadline
    }

    private fun showReconnecting() {
        waitingState?.let { _uiState.value = it.copy(isReconnecting = true) }
    }

    private fun clearReconnecting() {
        waitingState?.let { _uiState.value = it }
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
        val isReconnecting: Boolean = false,
    ) : DeviceFlowUiState

    /**
     * The user authorized the device; the credentials are already persisted by the repository, so nothing
     * has to be carried over to the next screen.
     */
    data object Authorized : DeviceFlowUiState
    data class Error(val message: String, val canRetry: Boolean) : DeviceFlowUiState
}

package io.homeassistant.companion.android.onboarding.cloudprovision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.onboarding.cloud.ApiException
import io.homeassistant.companion.android.onboarding.cloud.CloudOnboardingState
import io.homeassistant.companion.android.onboarding.cloud.WoowPaasApi
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val INITIAL_POLL_INTERVAL_MS = 5_000L
private const val MAX_POLL_INTERVAL_MS = 30_000L
private val POLL_TIMEOUT = 10.minutes

@OptIn(ExperimentalTime::class)
@HiltViewModel
internal class CloudProvisionViewModel internal constructor(private val api: WoowPaasApi, private val clock: Clock) :
    ViewModel() {

    /**
     * Production constructor used by Hilt. [WoowPaasApi] is created directly (never injected) to keep its
     * lazily-built [okhttp3.OkHttpClient] off the main thread, while [Clock] is provided by Hilt. The primary
     * constructor exposes both dependencies so unit tests can supply fakes without going through Hilt.
     */
    @Inject
    constructor(clock: Clock) : this(api = WoowPaasApi(), clock = clock)

    private var accessToken: String? = null

    private val _uiState = MutableStateFlow<ProvisionUiState>(ProvisionUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var isProvisionInProgress = false

    fun setAccessToken(sharedState: CloudOnboardingState) {
        accessToken = sharedState.accessToken
        if (accessToken == null) {
            _uiState.value = ProvisionUiState.Error(
                message = "登入已過期，請返回重新登入",
                canRetry = false,
            )
        }
    }

    fun onProvisionClicked() {
        val token = accessToken ?: return
        if (isProvisionInProgress) return
        isProvisionInProgress = true

        viewModelScope.launch {
            try {
                _uiState.value = ProvisionUiState.Provisioning

                api.provision(token).fold(
                    onSuccess = { response ->
                        when (response.status) {
                            "ready" -> {
                                val url = response.haUrl
                                if (url != null) {
                                    _uiState.value = ProvisionUiState.Ready(url)
                                } else {
                                    _uiState.value = ProvisionUiState.Error(
                                        message = "伺服器回報就緒但未提供網址",
                                        canRetry = true,
                                    )
                                }
                            }
                            "provisioning" -> {
                                startStatusPolling(token)
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
            } finally {
                if (_uiState.value !is ProvisionUiState.Provisioning) {
                    isProvisionInProgress = false
                }
            }
        }
    }

    /**
     * Polls the provisioning status until it becomes ready, fails terminally or the timeout elapses.
     *
     * Transient network failures (an [IOException] such as a socket timeout or DNS hiccup) do not abort the
     * wait: the loop keeps polling with an exponential backoff, bounded by [POLL_TIMEOUT] measured against the
     * injected [Clock]. Only terminal failures (e.g. an [ApiException] carrying a 401/403) stop the flow.
     */
    private fun startStatusPolling(token: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var interval = INITIAL_POLL_INTERVAL_MS
            val deadline = clock.now() + POLL_TIMEOUT

            try {
                while (true) {
                    delay(interval)

                    if (clock.now() >= deadline) {
                        _uiState.value = ProvisionUiState.Error(
                            message = "開通逾時（超過 10 分鐘），請重試或聯繫支援",
                            canRetry = true,
                        )
                        return@launch
                    }

                    api.getStatus(token).fold(
                        onSuccess = { response ->
                            when (response.status) {
                                "ready" -> {
                                    val url = response.haUrl
                                    if (url != null) {
                                        _uiState.value = ProvisionUiState.Ready(url)
                                    } else {
                                        _uiState.value = ProvisionUiState.Error(
                                            message = "伺服器回報就緒但未提供網址",
                                            canRetry = true,
                                        )
                                    }
                                    return@launch
                                }
                                "provisioning" -> {
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
                                else -> {
                                    _uiState.value = ProvisionUiState.Error(
                                        message = "異常狀態：${response.status}",
                                        canRetry = true,
                                    )
                                    return@launch
                                }
                            }
                        },
                        onFailure = { error ->
                            when {
                                // Never swallow cancellation: propagate so the poll loop cancels cleanly
                                error is CancellationException -> throw error
                                // Transient network failure: keep polling within the timeout budget
                                error is IOException -> {
                                    interval = (interval * 2).coerceAtMost(MAX_POLL_INTERVAL_MS)
                                }
                                // Terminal failure (e.g. ApiException 401/403): stop
                                else -> {
                                    _uiState.value = ProvisionUiState.Error(
                                        message = error.message ?: "查詢狀態失敗",
                                        canRetry = true,
                                    )
                                    return@launch
                                }
                            }
                        },
                    )
                }
            } finally {
                isProvisionInProgress = false
            }
        }
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

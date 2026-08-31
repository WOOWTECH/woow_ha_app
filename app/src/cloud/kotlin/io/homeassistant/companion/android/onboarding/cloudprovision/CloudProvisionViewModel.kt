package io.homeassistant.companion.android.onboarding.cloudprovision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.woowpaas.ApiException
import io.homeassistant.companion.android.common.data.woowpaas.ProvisionStatus
import io.homeassistant.companion.android.common.data.woowpaas.SessionExpiredException
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasRepository
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSessionRepository
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
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

private const val SESSION_EXPIRED_MESSAGE = "登入已過期，請返回重新登入"
private const val SERVICE_UNAVAILABLE_MESSAGE = "服務尚未開放，請稍後再試"
private const val MISSING_SCOPE_MESSAGE = "權限不足（缺少 ha:provision scope）"
private const val PROVISION_FAILED_MESSAGE = "開通失敗"
private const val STATUS_QUERY_FAILED_MESSAGE = "查詢狀態失敗"
private const val MISSING_URL_MESSAGE = "伺服器回報就緒但未提供網址"

@OptIn(ExperimentalTime::class)
@HiltViewModel
internal class CloudProvisionViewModel @Inject constructor(
    private val repository: WoowPaasRepository,
    private val sessionRepository: WoowPaasSessionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProvisionUiState>(ProvisionUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var isProvisionInProgress = false

    /**
     * Checks that the sign in performed earlier left something worth trying, and reports when it did not.
     *
     * The credentials live in storage rather than in memory, so a screen rebuilt after the process was
     * killed finds them again and the user carries on instead of signing in from scratch.
     *
     * The check is local and cannot be conclusive in the other direction: it only rules out the cases
     * nothing can be done about, namely no stored session at all or an expired access token with no refresh
     * token to replace it. A refresh token that the backend has since expired or revoked still looks fine
     * from here and is only discovered when the first call tries to use it, which surfaces the same
     * terminal error through [onProvisionClicked].
     */
    fun restoreSession() {
        viewModelScope.launch {
            val session = sessionRepository.currentSession()
            if (session?.canAuthenticateAt(clock.now()) != true) {
                _uiState.value = ProvisionUiState.Error(message = SESSION_EXPIRED_MESSAGE, canRetry = false)
            }
        }
    }

    fun onProvisionClicked() {
        if (isProvisionInProgress) return
        isProvisionInProgress = true

        viewModelScope.launch {
            try {
                _uiState.value = ProvisionUiState.Provisioning

                repository.provision().fold(
                    onSuccess = { response ->
                        when (val status = response.status) {
                            ProvisionStatus.Ready -> {
                                _uiState.value = readyStateFor(response.haUrl)
                            }
                            ProvisionStatus.Provisioning -> {
                                startStatusPolling()
                            }
                            ProvisionStatus.Suspended -> {
                                _uiState.value = ProvisionUiState.Suspended
                            }
                            ProvisionStatus.Deleting -> {
                                _uiState.value = ProvisionUiState.Deleting
                            }
                            // A provisioning request never answers "none" or "error"; surface the raw value.
                            ProvisionStatus.None,
                            ProvisionStatus.Error,
                            is ProvisionStatus.Unknown,
                            -> {
                                _uiState.value = ProvisionUiState.Error(
                                    message = "未預期的狀態: ${status.rawValue}",
                                    canRetry = true,
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = errorStateFor(error, fallbackMessage = PROVISION_FAILED_MESSAGE)
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
     * injected [Clock]. Only terminal failures stop the flow, and the repository refreshes the credentials on
     * its own, so an access token expiring mid wait is not one of them.
     */
    private fun startStatusPolling() {
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

                    repository.getStatus().fold(
                        onSuccess = { response ->
                            when (val status = response.status) {
                                ProvisionStatus.Ready -> {
                                    _uiState.value = readyStateFor(response.haUrl)
                                    return@launch
                                }
                                ProvisionStatus.Provisioning -> {
                                    interval = (interval * 2).coerceAtMost(MAX_POLL_INTERVAL_MS)
                                }
                                ProvisionStatus.Error -> {
                                    _uiState.value = ProvisionUiState.Error(
                                        message = response.error ?: "佈建失敗",
                                        canRetry = true,
                                    )
                                    return@launch
                                }
                                ProvisionStatus.Suspended -> {
                                    _uiState.value = ProvisionUiState.Suspended
                                    return@launch
                                }
                                ProvisionStatus.Deleting -> {
                                    _uiState.value = ProvisionUiState.Deleting
                                    return@launch
                                }
                                // "none" means the instance vanished mid-flow, which is as abnormal here
                                // as a status this version does not know about.
                                ProvisionStatus.None,
                                is ProvisionStatus.Unknown,
                                -> {
                                    _uiState.value = ProvisionUiState.Error(
                                        message = "異常狀態：${status.rawValue}",
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
                                // Terminal failure: stop
                                else -> {
                                    _uiState.value =
                                        errorStateFor(error, fallbackMessage = STATUS_QUERY_FAILED_MESSAGE)
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

    private fun readyStateFor(haUrl: String?): ProvisionUiState = if (haUrl != null) {
        ProvisionUiState.Ready(haUrl)
    } else {
        ProvisionUiState.Error(message = MISSING_URL_MESSAGE, canRetry = true)
    }

    /**
     * Turns a failure into the screen state describing it.
     *
     * A [SessionExpiredException] is the only case that cannot be retried: the repository already tried to
     * refresh the credentials and the backend refused, so the user has to sign in again. Everything else,
     * including a backend that could not be reached while refreshing, is worth another attempt.
     */
    private fun errorStateFor(error: Throwable, fallbackMessage: String): ProvisionUiState.Error {
        val message = when {
            error is SessionExpiredException -> SESSION_EXPIRED_MESSAGE
            error is ApiException && error.code == HTTP_UNAVAILABLE -> SERVICE_UNAVAILABLE_MESSAGE
            error is ApiException && error.code == HTTP_FORBIDDEN -> MISSING_SCOPE_MESSAGE
            else -> error.message ?: fallbackMessage
        }
        return ProvisionUiState.Error(message = message, canRetry = error !is SessionExpiredException)
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

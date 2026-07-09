package io.homeassistant.companion.android.onboarding.cloudsignin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
internal class CloudSignInViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun onSignInClick(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = SignInUiState.Loading
            // TODO: Replace with real Odoo API call when backend is ready
            delay(500)
            _uiState.value = SignInUiState.Success
        }
    }
}

internal sealed interface SignInUiState {
    data object Idle : SignInUiState
    data object Loading : SignInUiState
    data object Success : SignInUiState
}

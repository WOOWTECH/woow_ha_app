package io.homeassistant.companion.android.onboarding.cloudprovision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// TODO: Replace with real API call to Odoo /api/woow/tenant_service/provision
// and poll /status when integrating the actual cloud provisioning backend.
// This entire mock (delay + hardcoded URL) should be replaced with the real
// provisioning flow at that point.
internal const val MOCK_TEST_SERVER_URL = "https://woowtechdemo-ha1.woowtech.io"

@HiltViewModel
internal class CloudProvisionViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ProvisionUiState>(ProvisionUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun onProvisionClicked() {
        viewModelScope.launch {
            _uiState.value = ProvisionUiState.Provisioning
            delay(3000) // Mock K3s/Odoo provisioning delay
            _uiState.value = ProvisionUiState.Ready(MOCK_TEST_SERVER_URL)
        }
    }
}

internal sealed interface ProvisionUiState {
    data object Idle : ProvisionUiState
    data object Provisioning : ProvisionUiState
    data class Ready(val serverUrl: String) : ProvisionUiState
}

package com.pomodoro.nostr.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pomodoro.nostr.nostr.AmberCallbackResult
import com.pomodoro.nostr.nostr.KeyManager
import com.pomodoro.nostr.nostr.NostrClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val keyManager: KeyManager,
    private val nostrClient: NostrClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val isAuthenticated: StateFlow<Boolean> = keyManager.authState
        .map { it == KeyManager.AuthState.AUTHENTICATED_LOCAL || it == KeyManager.AuthState.AUTHENTICATED_AMBER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), keyManager.isAuthenticated())

    init {
        checkAmberAvailability()
        if (keyManager.isAuthenticated()) {
            connectToRelays()
        }
    }

    private fun checkAmberAvailability() {
        _uiState.value = _uiState.value.copy(
            isAmberAvailable = keyManager.isAmberInstalled()
        )
    }

    fun getAmberIntent(): Intent {
        return keyManager.createAmberGetPublicKeyIntent()
    }

    fun handleAmberActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode != android.app.Activity.RESULT_OK) {
            _uiState.value = _uiState.value.copy(
                error = "Amber authorization was cancelled"
            )
            return
        }

        var pubkey = data?.getStringExtra("result")
            ?: data?.getStringExtra("signature")
            ?: data?.getStringExtra("pubkey")
            ?: data?.getStringExtra("npub")

        if (pubkey.isNullOrBlank()) {
            val uri = data?.data
            pubkey = uri?.getQueryParameter("result")
                ?: uri?.getQueryParameter("pubkey")
                ?: uri?.getQueryParameter("npub")
        }

        if (pubkey.isNullOrBlank()) {
            val extras = data?.extras?.keySet()?.joinToString(", ") ?: "none"
            val dataUri = data?.data?.toString() ?: "none"
            _uiState.value = _uiState.value.copy(
                error = "No public key from Amber. Extras: $extras, Data: $dataUri"
            )
            return
        }

        keyManager.saveAmberPublicKeyAny(pubkey)
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    isAuthenticated = true,
                    npub = keyManager.getNpub(),
                    error = null
                )
                connectToRelays()
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    error = "Invalid public key from Amber: ${e.message}"
                )
            }
    }

    fun handleAmberResult(result: AmberCallbackResult) {
        when (result) {
            is AmberCallbackResult.PublicKey -> {
                keyManager.saveAmberPublicKey(result.npub)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isAuthenticated = true,
                            npub = result.npub,
                            error = null
                        )
                        connectToRelays()
                    }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            error = "Invalid public key from Amber: ${e.message}"
                        )
                    }
            }
            is AmberCallbackResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    error = "Amber error: ${result.message}"
                )
            }
            else -> { /* Other callback types handled elsewhere */ }
        }
    }

    fun importNsec(nsec: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        keyManager.importNsec(nsec)
            .onSuccess { keys ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    npub = keys.publicKey().toBech32(),
                    error = null
                )
                connectToRelays()
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Invalid nsec: ${e.message}"
                )
            }
    }

    fun generateNewAccount() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        try {
            val keys = keyManager.generateNewKeys()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isAuthenticated = true,
                npub = keys.publicKey().toBech32(),
                nsecGenerated = keys.secretKey().toBech32(),
                showBackupWarning = true,
                error = null
            )
            connectToRelays()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Failed to generate keys: ${e.message}"
            )
        }
    }

    fun dismissBackupWarning() {
        _uiState.value = _uiState.value.copy(
            showBackupWarning = false,
            nsecGenerated = null
        )
    }

    fun showNsecDialog() {
        _uiState.value = _uiState.value.copy(showNsecDialog = true)
    }

    fun hideNsecDialog() {
        _uiState.value = _uiState.value.copy(showNsecDialog = false, error = null)
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun logout() {
        nostrClient.disconnect()
        keyManager.clearKeys()
        _uiState.value = AuthUiState(isAmberAvailable = keyManager.isAmberInstalled())
    }

    private fun connectToRelays() {
        viewModelScope.launch {
            nostrClient.connect()
        }
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isAmberAvailable: Boolean = false,
    val npub: String? = null,
    val nsecGenerated: String? = null,
    val showBackupWarning: Boolean = false,
    val showNsecDialog: Boolean = false,
    val error: String? = null
)

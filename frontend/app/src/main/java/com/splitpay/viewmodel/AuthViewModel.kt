package com.splitpay.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// État possible de l'écran Auth
sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {

    // State observable par l'UI
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            // TODO: connecter à l'API backend plus tard
            if (email.isNotBlank() && password.length >= 8) {
                _uiState.value = AuthUiState.Success
            } else {
                _uiState.value = AuthUiState.Error("Email ou mot de passe invalide")
            }
        }
    }

    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            // TODO: connecter à l'API backend plus tard
            if (email.isNotBlank() && password.length >= 8 && displayName.isNotBlank()) {
                _uiState.value = AuthUiState.Success
            } else {
                _uiState.value = AuthUiState.Error("Tous les champs sont obligatoires")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
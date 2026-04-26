package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.local.AppCache
import com.splitpay.data.network.LoginRequest
import com.splitpay.data.network.RegisterRequest
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    data object Idle    : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenManager = (app as SplitPayApp).tokenManager
    private val api = RetrofitClient.api

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Email and password are required")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching { api.login(LoginRequest(email.trim(), password)) }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        val body = response.body()!!
                        tokenManager.save(body.accessToken, body.refreshToken, body.userId, body.name, body.email)
                        AppCache.clearAll()
                        _uiState.value = AuthUiState.Success
                    } else {
                        _uiState.value = AuthUiState.Error(
                            when (response.code()) {
                                401  -> "Incorrect email or password"
                                403  -> "Please verify your email"
                                else -> "Error ${response.code()}"
                            }
                        )
                    }
                }
                .onFailure { _uiState.value = AuthUiState.Error("Cannot reach the server") }
        }
    }

    fun register(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("All fields are required")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching { api.register(RegisterRequest(name.trim(), email.trim(), password)) }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        // Auto-login after register
                        login(email, password)
                    } else {
                        _uiState.value = AuthUiState.Error(
                            when (response.code()) {
                                400  -> "Weak password (8+ chars, 1 uppercase, 1 digit)"
                                409  -> "Email already in use"
                                else -> "Error ${response.code()}"
                            }
                        )
                    }
                }
                .onFailure { _uiState.value = AuthUiState.Error("Cannot reach the server") }
        }
    }

    fun resetState() { _uiState.value = AuthUiState.Idle }
}

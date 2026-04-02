package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.data.AppCache
import com.splitpay.network.LoginRequest
import com.splitpay.network.RegisterRequest
import com.splitpay.network.RetrofitClient
import com.splitpay.network.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    data object Idle    : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val api = RetrofitClient.build(tokenManager)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = api.login(LoginRequest(identifier.trim(), password))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    tokenManager.saveTokens(body.accessToken, body.refreshToken)
                    tokenManager.saveUserId(body.userId)
                    tokenManager.saveUserName(body.name)
                    tokenManager.saveUserEmail(body.email)
                    AppCache.onLogin(body.userId)
                    _uiState.value = AuthUiState.Success
                } else {
                    val msg = when (response.code()) {
                        401 -> "Invalid email or password"
                        404 -> "Account not found"
                        else -> "Login failed (${response.code()})"
                    }
                    _uiState.value = AuthUiState.Error(msg)
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Cannot reach server. Check your connection.")
            }
        }
    }

    fun register(email: String, password: String, displayName: String, phone: String? = null) {
        if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields")
            return
        }
        if (password.length < 8) {
            _uiState.value = AuthUiState.Error("Password must be at least 8 characters")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                // Step 1 — register (returns 201 + MessageResponse)
                val registerResponse = api.register(RegisterRequest(displayName.trim(), email.trim(), password, phone?.trim()?.ifBlank { null }))
                if (!registerResponse.isSuccessful) {
                    val msg = when (registerResponse.code()) {
                        409 -> "An account with this email already exists"
                        400 -> "Invalid information provided"
                        else -> "Registration failed (${registerResponse.code()})"
                    }
                    _uiState.value = AuthUiState.Error(msg)
                    return@launch
                }

                // Step 2 — auto login to get tokens
                val loginResponse = api.login(LoginRequest(email.trim(), password))
                if (loginResponse.isSuccessful) {
                    val body = loginResponse.body()!!
                    tokenManager.saveTokens(body.accessToken, body.refreshToken)
                    tokenManager.saveUserId(body.userId)
                    tokenManager.saveUserName(body.name)
                    tokenManager.saveUserEmail(body.email)
                    AppCache.onLogin(body.userId)
                    _uiState.value = AuthUiState.Success
                } else {
                    _uiState.value = AuthUiState.Error("Account created! Please log in.")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Cannot reach server. Check your connection.")
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            try { api.logout() } catch (_: Exception) {}
            tokenManager.clear()
            onDone()
        }
    }

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}

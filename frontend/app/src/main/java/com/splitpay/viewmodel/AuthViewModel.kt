package com.splitpay.viewmodel

import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.splitpay.SplitPayApp
import com.splitpay.data.local.AppCache
import com.splitpay.data.network.FcmTokenRequest
import com.splitpay.data.network.GoogleAuthRequest
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
                        // Register FCM token if available
                        tokenManager.fcmToken?.let { fcmToken ->
                            runCatching { api.registerFcmToken(FcmTokenRequest(fcmToken)) }
                        }
                        _uiState.value = AuthUiState.Success
                    } else {
                        val serverMsg = runCatching {
                            org.json.JSONObject(response.errorBody()?.string() ?: "").getString("message")
                        }.getOrNull()
                        _uiState.value = AuthUiState.Error(
                            when {
                                response.code() == 401 -> "Incorrect email or password"
                                serverMsg != null       -> serverMsg
                                else                   -> "Error ${response.code()}"
                            }
                        )
                    }
                }
                .onFailure { _uiState.value = AuthUiState.Error("Cannot reach the server") }
        }
    }

    fun register(name: String, email: String, password: String, phone: String? = null) {
        if (name.isBlank() || email.isBlank() || password.isBlank() || phone.isNullOrBlank()) {
            _uiState.value = AuthUiState.Error("All fields are required")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching { api.register(RegisterRequest(name.trim(), email.trim(), password, phone?.trim())) }
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

    fun loginWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = CredentialManager.create(context).getCredential(context, request)
                val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                val idToken = googleCredential.idToken

                val response = api.loginWithGoogle(GoogleAuthRequest(idToken))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    tokenManager.save(body.accessToken, body.refreshToken, body.userId, body.name, body.email)
                    AppCache.clearAll()
                    tokenManager.fcmToken?.let { runCatching { api.registerFcmToken(FcmTokenRequest(it)) } }
                    _uiState.value = AuthUiState.Success
                } else {
                    val msg = runCatching {
                        org.json.JSONObject(response.errorBody()?.string() ?: "").getString("message")
                    }.getOrNull()
                    _uiState.value = AuthUiState.Error(msg ?: "Google sign-in failed (${response.code()})")
                }
            } catch (e: GetCredentialCancellationException) {
                _uiState.value = AuthUiState.Idle
            } catch (e: NoCredentialException) {
                _uiState.value = AuthUiState.Error("No Google account found on this device")
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Google sign-in failed: ${e.localizedMessage}")
            }
        }
    }

    fun resetState() { _uiState.value = AuthUiState.Idle }

    companion object {
        // TODO: remplace par ton Web Client ID depuis Firebase Console →
        //  Authentication → Sign-in method → Google → Web SDK configuration → Web client ID
        const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID_HERE"
    }
}

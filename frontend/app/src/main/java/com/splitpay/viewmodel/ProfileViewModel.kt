package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.data.AppCache
import com.splitpay.network.RetrofitClient
import com.splitpay.network.TokenManager
import com.splitpay.network.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val api          = RetrofitClient.build(tokenManager)

    private val _userName  = MutableStateFlow(tokenManager.getUserName() ?: "")
    val userName: StateFlow<String> = _userName

    private val _userEmail = MutableStateFlow(tokenManager.getUserEmail() ?: "")
    val userEmail: StateFlow<String> = _userEmail

    private val _userPhone = MutableStateFlow(tokenManager.getUserPhone() ?: "")
    val userPhone: StateFlow<String> = _userPhone

    private val _totalBalance = MutableStateFlow(0.0)
    val totalBalance: StateFlow<Double> = _totalBalance

    private val _groupCount = MutableStateFlow(0)
    val groupCount: StateFlow<Int> = _groupCount

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode

    init { loadProfile() }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                val resp = api.getProfile()
                if (resp.isSuccessful) {
                    resp.body()?.let { user ->
                        _userName.value  = user.name
                        _userEmail.value = user.email
                        _userPhone.value = user.phone ?: ""
                        tokenManager.saveUserName(user.name)
                        tokenManager.saveUserEmail(user.email)
                        tokenManager.saveUserPhone(user.phone)
                    }
                }
                val groupsResp = api.getGroups()
                if (groupsResp.isSuccessful) {
                    _groupCount.value = groupsResp.body()?.size ?: 0
                }
            } catch (_: Exception) {
                // Fallback to cached values already loaded in init
            }
        }
    }

    fun toggleDarkMode() { _darkMode.value = !_darkMode.value }

    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            try { api.logout() } catch (_: Exception) {}
            tokenManager.clear()
            AppCache.clear()
            onLogout()
        }
    }
}

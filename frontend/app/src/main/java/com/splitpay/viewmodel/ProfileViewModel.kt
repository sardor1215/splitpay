package com.splitpay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitpay.SplitPayApp
import com.splitpay.data.local.AppCache
import com.splitpay.data.network.RefreshRequest
import com.splitpay.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenManager = (app as SplitPayApp).tokenManager
    private val api = RetrofitClient.api

    private val _userName     = MutableStateFlow(tokenManager.userName ?: "")
    val userName: StateFlow<String> = _userName

    private val _userEmail    = MutableStateFlow(tokenManager.userEmail ?: "")
    val userEmail: StateFlow<String> = _userEmail

    private val _totalBalance = MutableStateFlow(0.0)
    val totalBalance: StateFlow<Double> = _totalBalance

    private val _groupCount   = MutableStateFlow(AppCache.groups?.size ?: 0)
    val groupCount: StateFlow<Int> = _groupCount

    private val _darkMode     = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode

    init { loadProfile() }

    private fun loadProfile() {
        viewModelScope.launch {
            runCatching { api.getProfile() }.onSuccess { r ->
                if (r.isSuccessful) {
                    val body = r.body()!!
                    _userName.value  = body.name
                    _userEmail.value = body.email
                    tokenManager.userName  = body.name
                    tokenManager.userEmail = body.email
                }
            }
            // compute total balance across all groups
            val groups = AppCache.groups
            if (groups != null) {
                _totalBalance.value = groups.sumOf { it.balance }
                _groupCount.value   = groups.size
            }
        }
    }

    fun toggleDarkMode() { _darkMode.value = !_darkMode.value }

    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            val refresh = tokenManager.refreshToken
            if (refresh != null) {
                runCatching { api.logout(RefreshRequest(refresh)) }
            }
            tokenManager.clear()
            AppCache.clearAll()
            onLogout()
        }
    }
}

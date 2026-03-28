package com.splitpay.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProfileViewModel : ViewModel() {

    private val _userName = MutableStateFlow("Alex Rivera")
    val userName: StateFlow<String> = _userName

    private val _userEmail = MutableStateFlow("alex.rivera@email.com")
    val userEmail: StateFlow<String> = _userEmail

    private val _totalBalance = MutableStateFlow(1420.50)
    val totalBalance: StateFlow<Double> = _totalBalance

    private val _groupCount = MutableStateFlow(12)
    val groupCount: StateFlow<Int> = _groupCount

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode

    fun toggleDarkMode() {
        _darkMode.value = !_darkMode.value
    }

    fun logout(onLogout: () -> Unit) {
        onLogout()
    }
}

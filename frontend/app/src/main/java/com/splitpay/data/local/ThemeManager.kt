package com.splitpay.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeManager {
    private val _isDark = MutableStateFlow(false)
    val isDark = _isDark.asStateFlow()

    fun init(prefs: SharedPreferences) {
        _isDark.value = prefs.getBoolean("dark_mode", false)
    }

    fun toggle(prefs: SharedPreferences) {
        _isDark.value = !_isDark.value
        prefs.edit().putBoolean("dark_mode", _isDark.value).apply()
    }
}

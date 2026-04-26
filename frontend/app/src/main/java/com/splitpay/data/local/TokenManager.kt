package com.splitpay.data.local

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("splitpay_prefs", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(v) = prefs.edit().putString(KEY_ACCESS, v).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(v) = prefs.edit().putString(KEY_REFRESH, v).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(v) = prefs.edit().putString(KEY_USER_ID, v).apply()

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(v) = prefs.edit().putString(KEY_USER_NAME, v).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(v) = prefs.edit().putString(KEY_USER_EMAIL, v).apply()

    fun isLoggedIn(): Boolean = accessToken != null

    fun save(accessToken: String, refreshToken: String, userId: String, name: String, email: String) {
        this.accessToken  = accessToken
        this.refreshToken = refreshToken
        this.userId       = userId
        this.userName     = name
        this.userEmail    = email
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_ACCESS     = "access_token"
        private const val KEY_REFRESH    = "refresh_token"
        private const val KEY_USER_ID    = "user_id"
        private const val KEY_USER_NAME  = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
    }
}

package com.splitpay

import android.app.Application
import com.splitpay.data.local.AppCache
import com.splitpay.data.local.ThemeManager
import com.splitpay.data.local.TokenManager

class SplitPayApp : Application() {
    lateinit var tokenManager: TokenManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        tokenManager = TokenManager(this)
        AppCache.init(this)
        ThemeManager.init(getSharedPreferences("splitpay_theme", MODE_PRIVATE))
        invalidateStaleTokens()
    }

    // Efface les tokens si le serveur a changé (ex: Kotlin 8080 → Node 3000)
    private fun invalidateStaleTokens() {
        val prefs = getSharedPreferences("splitpay_prefs", MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", null)
        val currentUrl = com.splitpay.data.network.RetrofitClient.baseUrl
        if (savedUrl != null && savedUrl != currentUrl) {
            tokenManager.clear()
        }
        prefs.edit().putString("server_url", currentUrl).apply()
    }

    companion object {
        lateinit var instance: SplitPayApp
            private set
    }
}

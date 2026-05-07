package com.splitpay

import android.app.Application
import com.splitpay.data.local.AppCache
import com.splitpay.data.local.TokenManager

class SplitPayApp : Application() {
    lateinit var tokenManager: TokenManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        tokenManager = TokenManager(this)
        AppCache.init(this)
    }

    companion object {
        lateinit var instance: SplitPayApp
            private set
    }
}

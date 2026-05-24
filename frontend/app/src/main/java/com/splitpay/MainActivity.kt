package com.splitpay

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.splitpay.data.local.TokenManager
import com.splitpay.data.network.FcmTokenRequest
import com.splitpay.data.network.RetrofitClient
import com.splitpay.data.local.ThemeManager
import com.splitpay.navigation.NavGraph
import com.splitpay.ui.theme.SplitPayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Fetch FCM token and register with backend if logged in
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                val tm = TokenManager(this)
                tm.fcmToken = token
                Log.d("FCM_TOKEN", "Token: $token")
                if (tm.isLoggedIn()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { RetrofitClient.api.registerFcmToken(FcmTokenRequest(token)) }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FCM_TOKEN", "Failed to get token: ${e.message}")
            }

        setContent {
            val isDark by ThemeManager.isDark.collectAsState()
            SplitPayTheme(darkTheme = isDark) {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}

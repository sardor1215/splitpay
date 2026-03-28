package com.splitpay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.splitpay.navigation.NavGraph
import com.splitpay.ui.theme.SplitPayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitPayTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
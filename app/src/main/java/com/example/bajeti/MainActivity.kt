package com.example.bajeti

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clerk.api.Clerk
import com.example.bajeti.ui.navigation.AppNavigation
import com.example.bajeti.ui.theme.BajetiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Handle OAuth redirect when app is opened via the callback URI
        intent.data?.let { Clerk.auth.handle(it) }
        setContent {
            BajetiTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle OAuth redirect when app is already running
        intent.data?.let { Clerk.auth.handle(it) }
    }
}

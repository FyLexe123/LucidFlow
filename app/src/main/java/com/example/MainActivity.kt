package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.security.configureAppCheck
import com.example.ui.AuraApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check FIRST before rendering or calling Firebase AI Logic.
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            AuraApp()
        }
    }
}

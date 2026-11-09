package com.google.mytaskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.domain.model.ThemeMode
import com.google.mytaskmanager.presentation.common.AppViewModel
import com.google.mytaskmanager.presentation.ui.AppRoot
import com.google.mytaskmanager.ui.theme.MyTaskManagerTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host. Applies the user's theme choice (Light / Dark / Automatic) live. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ThemedApp() }
    }

    @Composable
    private fun ThemedApp() {
        val app: AppViewModel = hiltViewModel()
        val mode by app.themeMode.collectAsState()
        val dark = when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTOMATIC -> isSystemInDarkTheme()
        }
        // Keep status/navigation bar icon colours readable on the chosen theme.
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
            )
            onDispose { }
        }
        MyTaskManagerTheme(darkTheme = dark) { AppRoot(app) }
    }
}

package com.moblized.newsfeed

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moblized.newsfeed.data.ThemeMode
import com.moblized.newsfeed.ui.NewsApp
import com.moblized.newsfeed.ui.NewsTheme
import com.moblized.newsfeed.ui.NewsViewModel

class MainActivity : ComponentActivity() {
    /** Whether the app is showing its dark theme, so the home-screen icon can match. */
    private var darkTheme: Boolean? = null

    override fun onStop() {
        super.onStop()
        // Switch to the dark (or light) app icon once the app is out of sight.
        if (!isChangingConfigurations) darkTheme?.let { AppIcon.sync(this, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: NewsViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect { darkTheme = dark }
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            NewsTheme(dark = dark, dynamicColor = state.dynamicColor) {
                NewsApp(vm = vm, state = state)
            }
        }
    }
}

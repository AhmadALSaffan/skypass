package com.skypass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.TakeoffHost
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.navigation.SkypassNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.startScreen.value == null }
        enableEdgeToEdge()
        setContent {
            val startScreen by viewModel.startScreen.collectAsStateWithLifecycle()
            SkypassTheme {
                // The takeoff overlay sits above every screen so the search plane can fly over transitions.
                TakeoffHost {
                    startScreen?.let { SkypassNavHost(startScreen = it) }
                }
            }
        }
    }
}

package com.skypass

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.TakeoffHost
import com.skypass.core.designsystem.theme.LocalCurrency
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.ThemeMode
import com.skypass.core.ui.LocalUserVerifier
import com.skypass.navigation.SkypassNavHost
import com.skypass.security.BiometricVerifier
import com.skypass.security.LockScreen
import dagger.hilt.android.AndroidEntryPoint

/** A FragmentActivity because BiometricPrompt shows itself as a fragment. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.startScreen.value == null || viewModel.settings.value == null }
        enableEdgeToEdge()
        val verifier = BiometricVerifier(this)
        setContent {
            val startScreen by viewModel.startScreen.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val locked by viewModel.locked.collectAsStateWithLifecycle()
            val dark = when (settings?.device?.themeMode ?: ThemeMode.System) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Status and navigation bar icons follow the app's theme, not only the phone's.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            CompositionLocalProvider(
                LocalCurrency provides (settings?.account?.currency ?: com.skypass.core.model.DisplayCurrency.USD),
                LocalUserVerifier provides remember { verifier },
            ) {
                SkypassTheme(darkTheme = dark) {
                    // The takeoff overlay sits above every screen so the search plane can fly over transitions.
                    TakeoffHost {
                        startScreen?.let { SkypassNavHost(startScreen = it) }
                    }
                    if (locked) LockScreen(onUnlocked = viewModel::unlocked)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground(SystemClock.elapsedRealtime())
    }

    override fun onStop() {
        super.onStop()
        // Not when rotating: only a real trip to the background counts.
        if (!isChangingConfigurations) viewModel.onBackground(SystemClock.elapsedRealtime())
    }
}

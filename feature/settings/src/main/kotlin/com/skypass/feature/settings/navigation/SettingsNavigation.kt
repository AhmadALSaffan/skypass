package com.skypass.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.settings.SettingsRoute
import kotlinx.serialization.Serializable

/** Settings, from the Profile gear. */
@Serializable
data object SettingsDestination

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, onSignedOut: () -> Unit) {
    composable<SettingsDestination> { SettingsRoute(onBack = onBack, onSignedOut = onSignedOut) }
}

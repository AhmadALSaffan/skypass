package com.skypass.feature.confirmation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.confirmation.ConfirmationRoute
import kotlinx.serialization.Serializable

/** Booking ids, comma-separated (one per leg). */
@Serializable
data class ConfirmationDestination(val bookingIds: String)

fun NavGraphBuilder.confirmationScreen(onHome: () -> Unit) {
    composable<ConfirmationDestination>(enterTransition = { fadeIn(tween(400)) }) {
        ConfirmationRoute(onHome = onHome)
    }
}

package com.skypass.feature.hotels.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.hotels.HotelsRoute
import kotlinx.serialization.Serializable

/** Stays in [city]; without one, the arrival city of the next trip (or Auckland). */
@Serializable
data class HotelsDestination(val city: String? = null)

fun NavGraphBuilder.hotelsScreen(onBack: () -> Unit, onOpenMyTrips: () -> Unit) {
    composable<HotelsDestination> { HotelsRoute(onBack = onBack, onOpenMyTrips = onOpenMyTrips) }
}

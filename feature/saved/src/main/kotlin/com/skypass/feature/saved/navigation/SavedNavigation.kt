package com.skypass.feature.saved.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.saved.SavedRoute
import kotlinx.serialization.Serializable

/** The Saved tab. */
@Serializable
data object SavedDestination

fun NavGraphBuilder.savedScreen(
    onBookFlight: (instanceId: String) -> Unit,
    onOpenHotels: (city: String?) -> Unit,
    onSearchFlights: () -> Unit,
    onOpenExplore: () -> Unit,
) {
    composable<SavedDestination> {
        SavedRoute(onBookFlight = onBookFlight, onOpenHotels = onOpenHotels, onSearchFlights = onSearchFlights, onOpenExplore = onOpenExplore)
    }
}

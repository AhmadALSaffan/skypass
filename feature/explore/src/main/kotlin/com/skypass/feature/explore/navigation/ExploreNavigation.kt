package com.skypass.feature.explore.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.explore.ExploreRoute
import kotlinx.serialization.Serializable

@Serializable
data object ExploreDestination

fun NavGraphBuilder.exploreScreen(
    onOpenFlights: () -> Unit,
    onOpenHotels: () -> Unit,
    onOpenCars: () -> Unit,
) {
    composable<ExploreDestination> {
        ExploreRoute(onOpenFlights = onOpenFlights, onOpenHotels = onOpenHotels, onOpenCars = onOpenCars)
    }
}

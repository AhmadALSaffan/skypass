package com.skypass.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.core.model.FlightSearchQuery
import com.skypass.feature.home.HomeRoute
import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination

fun NavGraphBuilder.homeScreen(
    onSearch: (FlightSearchQuery) -> Unit,
    onSeeAllTrips: () -> Unit,
    onSeeAllDestinations: () -> Unit,
) {
    composable<HomeDestination> {
        HomeRoute(onSearch = onSearch, onSeeAllTrips = onSeeAllTrips, onSeeAllDestinations = onSeeAllDestinations)
    }
}

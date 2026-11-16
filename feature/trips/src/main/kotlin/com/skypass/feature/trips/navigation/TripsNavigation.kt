package com.skypass.feature.trips.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.trips.TripsRoute
import kotlinx.serialization.Serializable

/** My trips, opened on [tab] ("Flights", "Stays" or "Cars"); Flights by default. */
@Serializable
data class TripsDestination(val tab: String? = null)

fun NavGraphBuilder.tripsScreen(
    onBack: () -> Unit,
    onBoardingPass: (bookingId: String) -> Unit,
    onFindFlights: () -> Unit,
    onFindStays: () -> Unit,
    onFindCars: () -> Unit,
) {
    composable<TripsDestination> {
        TripsRoute(
            onBack = onBack,
            onBoardingPass = onBoardingPass,
            onFindFlights = onFindFlights,
            onFindStays = onFindStays,
            onFindCars = onFindCars,
        )
    }
}

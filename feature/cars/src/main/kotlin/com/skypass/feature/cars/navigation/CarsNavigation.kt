package com.skypass.feature.cars.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.cars.CarsRoute
import kotlinx.serialization.Serializable

/** Car rental, picking up at the arrival airport of the next trip (or Auckland). */
@Serializable
data object CarsDestination

fun NavGraphBuilder.carsScreen(onBack: () -> Unit) {
    composable<CarsDestination> { CarsRoute(onBack = onBack) }
}

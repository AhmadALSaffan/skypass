package com.skypass.feature.profile.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.profile.PassengersRoute
import com.skypass.feature.profile.ProfileRoute
import kotlinx.serialization.Serializable

/** The Profile tab. */
@Serializable
data object ProfileDestination

/** Passengers & travel documents. */
@Serializable
data object PassengersDestination

fun NavGraphBuilder.profileScreen(
    onSettings: () -> Unit,
    onPassengers: () -> Unit,
    onSaved: () -> Unit,
    onTrips: () -> Unit,
) {
    composable<ProfileDestination> {
        ProfileRoute(onSettings = onSettings, onPassengers = onPassengers, onSaved = onSaved, onTrips = onTrips)
    }
}

fun NavGraphBuilder.passengersScreen(onBack: () -> Unit) {
    composable<PassengersDestination> { PassengersRoute(onBack = onBack) }
}

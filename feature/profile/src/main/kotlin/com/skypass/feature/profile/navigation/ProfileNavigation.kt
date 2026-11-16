package com.skypass.feature.profile.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.profile.PassengersRoute
import com.skypass.feature.profile.PaymentMethodsRoute
import com.skypass.feature.profile.ProfileRoute
import kotlinx.serialization.Serializable

/** The Profile tab. */
@Serializable
data object ProfileDestination

/** Passengers & travel documents. */
@Serializable
data object PassengersDestination

/** Payment methods: saved cards and Google Pay. */
@Serializable
data object PaymentMethodsDestination

fun NavGraphBuilder.profileScreen(
    onSettings: () -> Unit,
    onPassengers: () -> Unit,
    onPaymentMethods: () -> Unit,
    onSaved: () -> Unit,
    onTrips: () -> Unit,
) {
    composable<ProfileDestination> {
        ProfileRoute(
            onSettings = onSettings,
            onPassengers = onPassengers,
            onPaymentMethods = onPaymentMethods,
            onSaved = onSaved,
            onTrips = onTrips,
        )
    }
}

fun NavGraphBuilder.paymentMethodsScreen(onBack: () -> Unit) {
    composable<PaymentMethodsDestination> { PaymentMethodsRoute(onBack = onBack) }
}

fun NavGraphBuilder.passengersScreen(onBack: () -> Unit) {
    composable<PassengersDestination> { PassengersRoute(onBack = onBack) }
}

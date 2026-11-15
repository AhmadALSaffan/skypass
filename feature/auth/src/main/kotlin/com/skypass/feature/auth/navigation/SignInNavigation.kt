package com.skypass.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.auth.SignInRoute
import kotlinx.serialization.Serializable

@Serializable
data object SignInDestination

fun NavGraphBuilder.signInScreen(onSignedIn: () -> Unit) {
    composable<SignInDestination> {
        SignInRoute(onSignedIn = onSignedIn)
    }
}

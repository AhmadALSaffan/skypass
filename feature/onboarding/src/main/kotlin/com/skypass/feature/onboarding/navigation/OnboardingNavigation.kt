package com.skypass.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.feature.onboarding.OnboardingRoute
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingDestination

fun NavGraphBuilder.onboardingScreen(onFinished: () -> Unit) {
    composable<OnboardingDestination> {
        OnboardingRoute(onFinished = onFinished)
    }
}

package com.skypass.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.skypass.core.designsystem.component.BottomBarItem
import com.skypass.core.designsystem.component.SkypassBottomBar
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.StartScreen
import com.skypass.feature.auth.navigation.SignInDestination
import com.skypass.feature.auth.navigation.signInScreen
import com.skypass.feature.home.navigation.HomeDestination
import com.skypass.feature.booking.navigation.BookingDestination
import com.skypass.feature.booking.navigation.bookingScreen
import com.skypass.feature.confirmation.navigation.ConfirmationDestination
import com.skypass.feature.confirmation.navigation.confirmationScreen
import com.skypass.feature.payment.navigation.PaymentDestination
import com.skypass.feature.payment.navigation.paymentScreen
import com.skypass.feature.home.navigation.homeScreen
import com.skypass.feature.explore.navigation.ExploreDestination
import com.skypass.feature.explore.navigation.exploreScreen
import com.skypass.feature.hotels.navigation.HotelsDestination
import com.skypass.feature.hotels.navigation.hotelsScreen
import com.skypass.feature.cars.navigation.CarsDestination
import com.skypass.feature.cars.navigation.carsScreen
import com.skypass.feature.onboarding.navigation.OnboardingDestination
import com.skypass.feature.onboarding.navigation.onboardingScreen
import com.skypass.feature.results.navigation.ResultsDestination
import com.skypass.feature.results.navigation.resultsScreen
import com.skypass.feature.results.navigation.toResultsDestination
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object SavedDestination
@Serializable data object ProfileDestination

/** The four tabs from the board, icon-only with an active dot. */
private enum class Tab(val route: Any, val routeClass: KClass<*>, val item: BottomBarItem) {
    Home(HomeDestination, HomeDestination::class, BottomBarItem("Home", SkypassIcons.Home, SkypassIcons.HomeFilled)),
    Explore(ExploreDestination, ExploreDestination::class, BottomBarItem("Explore", SkypassIcons.Compass, SkypassIcons.Compass)),
    Saved(SavedDestination, SavedDestination::class, BottomBarItem("Saved", SkypassIcons.Bookmark, SkypassIcons.BookmarkFilled)),
    Profile(ProfileDestination, ProfileDestination::class, BottomBarItem("Profile", SkypassIcons.User, SkypassIcons.User)),
}

private fun NavDestination?.tab(): Tab? = Tab.entries.firstOrNull { tab -> this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }

@Composable
fun SkypassNavHost(startScreen: StartScreen) {
    val navController = rememberNavController()
    val start: Any = when (startScreen) {
        StartScreen.Onboarding -> OnboardingDestination
        StartScreen.SignIn -> SignInDestination
        StartScreen.Home -> HomeDestination
    }
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = backStack?.destination.tab()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.fillMaxSize().padding(bottom = if (currentTab != null) BottomBarHeight + navBarInset else 0.dp),
            enterTransition = {
                if (initialState.destination.tab() != null && targetState.destination.tab() != null) {
                    fadeIn(tween(Motion.SCREEN))
                } else {
                    slideIntoContainer(SlideDirection.Start, tween(Motion.SCREEN, easing = Motion.EmphasizedDecelerate)) { it / 6 } +
                        fadeIn(tween(Motion.SCREEN, easing = Motion.EmphasizedDecelerate))
                }
            },
            exitTransition = {
                if (targetState.destination.hasRoute(ResultsDestination::class)) {
                    // Home slides away as the search plane pulls the results in.
                    slideOutHorizontally(tween(520, easing = Motion.EmphasizedAccelerate)) { -it / 5 } + fadeOut(tween(360))
                } else {
                    fadeOut(tween(Motion.EXIT))
                }
            },
            popEnterTransition = { fadeIn(tween(Motion.SCREEN)) },
            popExitTransition = {
                slideOutOfContainer(SlideDirection.End, tween(Motion.SCREEN, easing = Motion.EmphasizedAccelerate)) { it / 6 } +
                    fadeOut(tween(Motion.EXIT))
            },
        ) {
            onboardingScreen(onFinished = { navController.replaceAll(SignInDestination) })
            signInScreen(onSignedIn = { navController.replaceAll(HomeDestination) })

            homeScreen(
                onSearch = { navController.navigate(it.toResultsDestination()) },
                onSeeAllTrips = { navController.switchTab(Tab.Saved) },
                onSeeAllDestinations = { navController.switchTab(Tab.Explore) },
            )
            exploreScreen(
                onOpenFlights = { navController.switchTab(Tab.Home) },
                onOpenHotels = { navController.navigate(HotelsDestination()) },
                onOpenCars = { navController.navigate(CarsDestination) },
            )
            hotelsScreen(onBack = { navController.popBackStack() })
            carsScreen(onBack = { navController.popBackStack() })
            composable<SavedDestination> { ComingSoon("Saved", phase = 11) }
            composable<ProfileDestination> {
                ProfilePlaceholder(onSignedOut = { navController.replaceAll(SignInDestination) })
            }
            resultsScreen(
                onBack = { navController.popBackStack() },
                onPickReturn = { navController.navigate(it) },
                onFlightSelected = { selection ->
                    navController.navigate(
                        BookingDestination(selection.outboundInstanceId, selection.returnInstanceId, selection.adults, selection.cabin.apiValue),
                    )
                },
            )
            bookingScreen(
                onBack = { navController.popBackStack() },
                onContinue = { r -> navController.navigate(PaymentDestination.from(r.legs, r.passengerIds, r.cabin, r.bags)) },
            )
            paymentScreen(
                onBack = { navController.popBackStack() },
                onPaid = { ids ->
                    // Paid: the booking flow is done, so Back from the boarding pass goes home.
                    navController.navigate(ConfirmationDestination(ids.joinToString(","))) {
                        popUpTo(HomeDestination)
                    }
                },
            )
            // A fresh Home so the new trip shows up straight away.
            confirmationScreen(onHome = { navController.replaceAll(HomeDestination) })
        }

        // Tab screens scroll edge to edge; keep the clock readable over them.
        if (currentTab != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(SkypassTheme.colors.bg),
            )
        }

        AnimatedVisibility(
            visible = currentTab != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SkypassBottomBar(
                items = Tab.entries.map { it.item },
                selectedIndex = (currentTab ?: Tab.Home).ordinal,
                onSelect = { navController.switchTab(Tab.entries[it]) },
            )
        }
    }
}

/** Bar height above the system navigation bar (its insets are padded inside the bar). */
private val BottomBarHeight = 62.dp

/**
 * Standard tab switching: one copy of each tab, state saved and restored. Pops to Home, the tabs'
 * root, rather than the graph's start: after sign-in the start (onboarding / sign-in) is no longer
 * on the back stack, and popping to it would stack a second Home on every switch.
 */
private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo<HomeDestination> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Navigate to [route] and clear the back stack, so Back doesn't return to sign-in or onboarding. */
private fun NavHostController.replaceAll(route: Any) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

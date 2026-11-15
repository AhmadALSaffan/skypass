package com.skypass.feature.results.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.model.Cabin
import com.skypass.core.model.FlightSearchQuery
import com.skypass.core.model.TripLeg
import com.skypass.feature.results.ResultsRoute
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Results for one leg. Round trips open this twice: outbound, then return. Dates are ISO strings. */
@Serializable
data class ResultsDestination(
    val from: String,
    val to: String,
    val date: String,
    /** The trip's return date (round trips only), carried so the outbound leg can open the return leg. */
    val returnDate: String? = null,
    val adults: Int,
    val cabin: String,
    val leg: TripLeg = TripLeg.Outbound,
    val outboundInstanceId: String? = null,
)

fun FlightSearchQuery.toResultsDestination() = ResultsDestination(
    from = from.code,
    to = to.code,
    date = departDate.toString(),
    returnDate = returnDate?.toString(),
    adults = adults,
    cabin = cabin.apiValue,
)

/** What the traveller picked; Phase 5 (booking) starts from this. */
data class FlightSelection(
    val outboundInstanceId: String,
    val returnInstanceId: String?,
    val adults: Int,
    val cabin: Cabin,
)

/** Typed view of the route arguments stored in the ViewModel's SavedStateHandle. */
internal class ResultsArgs(handle: SavedStateHandle) {
    val from: String = checkNotNull(handle["from"])
    val to: String = checkNotNull(handle["to"])
    val date: LocalDate = LocalDate.parse(checkNotNull<String>(handle["date"]))
    val returnDate: LocalDate? = handle.get<String>("returnDate")?.let(LocalDate::parse)
    val adults: Int = checkNotNull(handle["adults"])
    val cabin: Cabin = Cabin.fromApi(checkNotNull(handle["cabin"]))
    val leg: TripLeg = handle.get<Any>("leg").let { if (it == TripLeg.Return || it == "Return") TripLeg.Return else TripLeg.Outbound }
    val outboundInstanceId: String? = handle["outboundInstanceId"]
}

fun NavGraphBuilder.resultsScreen(
    onBack: () -> Unit,
    onPickReturn: (ResultsDestination) -> Unit,
    onFlightSelected: (FlightSelection) -> Unit,
) {
    composable<ResultsDestination>(
        // The page arrives behind the search plane: a slide from the right, a beat after it lifts off.
        enterTransition = {
            slideInHorizontally(tween(560, easing = Motion.EmphasizedDecelerate)) { it / 3 } +
                fadeIn(tween(420, delayMillis = 120))
        },
    ) {
        ResultsRoute(onBack = onBack, onPickReturn = onPickReturn, onFlightSelected = onFlightSelected)
    }
}

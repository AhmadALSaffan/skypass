package com.skypass.feature.booking.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.SavedStateHandle
import com.skypass.core.designsystem.theme.LocalNavAnimatedScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.core.model.Cabin
import com.skypass.core.model.LegOrder
import com.skypass.feature.booking.BookingRoute
import kotlinx.serialization.Serializable

/** Booking details for the chosen departure(s). */
@Serializable
data class BookingDestination(
    val outboundInstanceId: String,
    val returnInstanceId: String? = null,
    val adults: Int,
    /** Cabin api value, e.g. "economy". */
    val cabin: String,
)

/** What payment needs: each leg with a seat per traveller (same order as [passengerIds]). */
data class PaymentRequest(
    val legs: List<LegOrder>,
    val passengerIds: List<String>,
    val cabin: Cabin,
    val bags: Int,
)

internal class BookingArgs(handle: SavedStateHandle) {
    val outboundInstanceId: String = checkNotNull(handle["outboundInstanceId"])
    val returnInstanceId: String? = handle["returnInstanceId"]
    val adults: Int = checkNotNull(handle["adults"])
    val cabin: Cabin = Cabin.fromApi(checkNotNull(handle["cabin"]))
}

fun NavGraphBuilder.bookingScreen(
    onBack: () -> Unit,
    onContinue: (PaymentRequest) -> Unit,
) {
    composable<BookingDestination> {
        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
            BookingRoute(onBack = onBack, onContinue = onContinue)
        }
    }
}

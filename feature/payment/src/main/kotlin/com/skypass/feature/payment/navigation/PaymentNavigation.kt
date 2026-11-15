package com.skypass.feature.payment.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.skypass.core.model.Cabin
import com.skypass.core.model.LegOrder
import com.skypass.feature.payment.PaymentRoute
import kotlinx.serialization.Serializable

/** Seats and passenger ids are comma-separated, in traveller order. */
@Serializable
data class PaymentDestination(
    val outboundInstanceId: String,
    val outboundSeats: String,
    val returnInstanceId: String? = null,
    val returnSeats: String? = null,
    val passengerIds: String,
    val cabin: String,
    val bags: Int,
) {
    companion object {
        fun from(legs: List<LegOrder>, passengerIds: List<String>, cabin: Cabin, bags: Int) = PaymentDestination(
            outboundInstanceId = legs[0].instanceId,
            outboundSeats = legs[0].seats.joinToString(","),
            returnInstanceId = legs.getOrNull(1)?.instanceId,
            returnSeats = legs.getOrNull(1)?.seats?.joinToString(","),
            passengerIds = passengerIds.joinToString(","),
            cabin = cabin.apiValue,
            bags = bags,
        )
    }
}

internal class PaymentArgs(handle: SavedStateHandle) {
    val legs: List<LegOrder> = buildList {
        add(LegOrder(checkNotNull(handle["outboundInstanceId"]), checkNotNull<String>(handle["outboundSeats"]).split(",")))
        val back: String? = handle["returnInstanceId"]
        if (back != null) add(LegOrder(back, checkNotNull<String>(handle["returnSeats"]).split(",")))
    }
    val passengerIds: List<String> = checkNotNull<String>(handle["passengerIds"]).split(",").filter { it.isNotBlank() }
    val cabin: Cabin = Cabin.fromApi(checkNotNull(handle["cabin"]))
    val bags: Int = checkNotNull(handle["bags"])
}

fun NavGraphBuilder.paymentScreen(
    onBack: () -> Unit,
    onPaid: (bookingIds: List<String>) -> Unit,
) {
    composable<PaymentDestination> {
        PaymentRoute(onBack = onBack, onPaid = onPaid)
    }
}

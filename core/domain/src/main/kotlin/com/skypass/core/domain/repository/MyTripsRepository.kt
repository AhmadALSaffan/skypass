package com.skypass.core.domain.repository

import com.skypass.core.model.AppError
import com.skypass.core.model.CarBooking
import com.skypass.core.model.FlightBooking
import com.skypass.core.model.Outcome
import com.skypass.core.model.StayBooking

/** Everything the traveller has booked, kept on the server so it follows them between phones. */
interface MyTripsRepository {
    suspend fun flights(): Outcome<List<FlightBooking>, AppError>

    suspend fun stays(): Outcome<List<StayBooking>, AppError>

    suspend fun cars(): Outcome<List<CarBooking>, AppError>

    /** Cancels a stay or a car booked with free cancellation, by its reference. */
    suspend fun cancel(reference: String): Outcome<Unit, CancelError>
}

enum class CancelError { NotCancellable, Network, Unknown }

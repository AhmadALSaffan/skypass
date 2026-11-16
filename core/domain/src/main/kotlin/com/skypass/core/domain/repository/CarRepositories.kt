package com.skypass.core.domain.repository

import com.skypass.core.model.AppError
import com.skypass.core.model.Car
import com.skypass.core.model.CarReservation
import com.skypass.core.model.DriverDetails
import com.skypass.core.model.Outcome
import com.skypass.core.model.Rental
import com.skypass.core.model.ReservationError

interface CarRepository {
    /** Cars to pick up at [airportCode], in the board's order. */
    suspend fun cars(airportCode: String): Outcome<List<Car>, AppError>

    /**
     * Books [car] for [rental] with the driver and extras in [details]. Priced on the server, which
     * keeps the details for My trips; the same [attemptId] never books twice.
     */
    suspend fun reserve(car: Car, rental: Rental, details: DriverDetails, attemptId: String): Outcome<CarReservation, ReservationError>
}

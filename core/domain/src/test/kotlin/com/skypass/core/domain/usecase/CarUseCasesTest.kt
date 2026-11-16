package com.skypass.core.domain.usecase

import com.skypass.core.model.Airport
import com.skypass.core.model.CarType
import com.skypass.core.model.Rental
import com.skypass.core.model.RentalPlace
import com.skypass.core.testing.FakeTripRepository
import com.skypass.core.testing.TestAirports
import com.skypass.core.testing.TestCars
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class CarUseCasesTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val akl = RentalPlace("Auckland Airport (AKL)", "AKL")

    private fun rental(dropoff: RentalPlace = akl) = Rental(
        pickup = akl,
        dropoff = dropoff,
        pickupAt = LocalDateTime.of(2026, 11, 20, 14, 0),
        dropoffAt = LocalDateTime.of(2026, 11, 27, 10, 0),
    )

    @Test
    fun `without a trip, the board's card is Auckland, Fri 20 14h00 to Fri 27 10h00`() = runTest {
        val trips = FakeTripRepository().apply { emit(null) }
        assertEquals(rental(), ResolveRentalUseCase(trips, clock)(TestAirports.all))
    }

    @Test
    fun `a week from 14h00 to 10h00 is 7 days, and a minute over is 8`() {
        assertEquals(7, rental().days)
        assertEquals(8, rental().copy(dropoffAt = LocalDateTime.of(2026, 11, 27, 14, 1)).days)
    }

    @Test
    fun `the board's total, plus a fee when the car goes back elsewhere`() {
        val price = PriceRentalUseCase()
        assertEquals(287, price(TestCars.CERATO, rental()).totalUsd) // $41 × 7
        val oneWay = price(TestCars.CERATO, rental(dropoff = RentalPlace("Auckland CBD, Queen St")))
        assertEquals(49, oneWay.oneWayFeeUsd)
        assertEquals(336, oneWay.totalUsd)
    }

    @Test
    fun `drop-off can be the same airport, the city centre or another airport in the country`() {
        val wlg = Airport("WLG", "Wellington", "New Zealand", "Wellington Airport")
        val places = DropOffPlacesUseCase()(akl, TestAirports.all + wlg)
        assertEquals(
            listOf("Auckland Airport (AKL)", "Auckland CBD, Queen St", "Queenstown Airport (ZQN)", "Wellington Airport (WLG)"),
            places.map { it.label },
        )
    }

    @Test
    fun `type chips and date checks`() {
        assertEquals(listOf("Jeep Wrangler", "Nissan Juke"), FilterCarsUseCase()(TestCars.auckland, CarType.SUV).map { it.name })
        val validate = ValidateRentalUseCase(clock)
        assertNull(validate(rental()))
        assertEquals(RentalProblem.DropoffBeforePickup, validate(rental().copy(dropoffAt = LocalDateTime.of(2026, 11, 20, 13, 0))))
        assertEquals(RentalProblem.PickupInPast, validate(rental().copy(pickupAt = LocalDateTime.of(2026, 11, 1, 14, 0))))
        assertEquals(RentalProblem.TooLong, validate(rental().copy(dropoffAt = LocalDateTime.of(2026, 12, 30, 10, 0))))
    }
}

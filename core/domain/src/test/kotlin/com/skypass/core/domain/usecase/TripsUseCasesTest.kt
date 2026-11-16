package com.skypass.core.domain.usecase

import com.skypass.core.model.AppError
import com.skypass.core.model.ContactDetails
import com.skypass.core.model.Outcome
import com.skypass.core.model.ReservationStatus
import com.skypass.core.testing.FakeMyTripsRepository
import com.skypass.core.testing.FakePassengerRepository
import com.skypass.core.testing.FakeProfileRepository
import com.skypass.core.testing.TestPassengers
import com.skypass.core.testing.TestTrips
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TripsUseCasesTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))

    @Test
    fun `contact checks - a full name, a real-looking email, and a phone only if given`() {
        val validate = ValidateContactUseCase()
        assertTrue(validate(TestTrips.TINA).isEmpty())
        assertTrue(validate(TestTrips.TINA.copy(phone = "")).isEmpty())
        assertEquals(
            setOf(ContactProblem.NameMissing, ContactProblem.EmailInvalid, ContactProblem.PhoneInvalid),
            validate(ContactDetails(" T ", "tina@example", "12ab")),
        )
    }

    @Test
    fun `the form is prefilled from the primary passenger, else the account`() = runTest {
        val fromPassenger = PrefillContactUseCase(FakePassengerRepository(listOf(TestPassengers.TINA)), FakeProfileRepository())()
        assertEquals(ContactDetails("Tina Anderson", "tina.anderson@gmail.com", "+64 21 555 0199"), fromPassenger)

        val fromAccount = PrefillContactUseCase(FakePassengerRepository(), FakeProfileRepository())()
        assertEquals(ContactDetails("Tina Anderson", "tina.anderson@gmail.com", ""), fromAccount)
    }

    @Test
    fun `trips split into upcoming, soonest first, and past or cancelled, latest first`() = runTest {
        val cancelled = TestTrips.STAY.copy(reference = "SKHX1", checkIn = LocalDate.of(2026, 12, 1), checkOut = LocalDate.of(2026, 12, 3), status = ReservationStatus.Cancelled)
        val later = TestTrips.STAY.copy(reference = "SKHL8", checkIn = LocalDate.of(2026, 12, 10), checkOut = LocalDate.of(2026, 12, 12))
        val repo = FakeMyTripsRepository(stays = listOf(later, TestTrips.PAST_STAY, cancelled, TestTrips.STAY))
        val trips = (GetMyTripsUseCase(repo, clock)() as Outcome.Success).value

        assertEquals(listOf("SKHK7M", "SKHL8"), trips.stays.upcoming.map { it.reference })
        assertEquals(listOf("SKHX1", "SKHP2A"), trips.stays.past.map { it.reference })
        assertEquals(1, trips.flights.upcoming.size)
        assertEquals(1, trips.cars.upcoming.size)
    }

    @Test
    fun `one list failing still shows the others, all failing is an error`() = runTest {
        val repo = FakeMyTripsRepository(failure = AppError.Network)
        assertEquals(Outcome.Failure(AppError.Network), GetMyTripsUseCase(repo, clock)())
    }

    @Test
    fun `free cancellation - stays until the day before check-in, cars until 48 hours before pick-up`() {
        assertTrue(TestTrips.STAY.canCancel(LocalDate.of(2026, 11, 20)))
        assertFalse(TestTrips.STAY.canCancel(LocalDate.of(2026, 11, 21)))
        assertEquals(LocalDate.of(2026, 11, 20), TestTrips.STAY.freeCancellationUntil)

        assertTrue(TestTrips.CAR.canCancel(LocalDateTime.of(2026, 11, 18, 13, 59)))
        assertFalse(TestTrips.CAR.canCancel(LocalDateTime.of(2026, 11, 18, 14, 0)))
        assertFalse(TestTrips.CAR.copy(status = ReservationStatus.Cancelled).canCancel(LocalDateTime.of(2026, 11, 15, 10, 0)))
    }
}

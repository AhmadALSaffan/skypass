package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.CancelError
import com.skypass.core.domain.repository.MyTripsRepository
import com.skypass.core.domain.repository.PassengerRepository
import com.skypass.core.domain.repository.ProfileRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.CarBooking
import com.skypass.core.model.ContactDetails
import com.skypass.core.model.FlightBooking
import com.skypass.core.model.Outcome
import com.skypass.core.model.ReservationStatus
import com.skypass.core.model.StayBooking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

enum class ContactProblem { NameMissing, EmailInvalid, PhoneInvalid }

/** The guest or driver form: a full name, a real-looking email, and a phone number if given. */
class ValidateContactUseCase @Inject constructor() {
    operator fun invoke(contact: ContactDetails): Set<ContactProblem> = buildSet {
        if (contact.name.trim().length < 2) add(ContactProblem.NameMissing)
        if (!EMAIL.matches(contact.email.trim())) add(ContactProblem.EmailInvalid)
        val digits = contact.phone.count(Char::isDigit)
        if (contact.phone.isNotBlank() && (digits < 6 || !PHONE.matches(contact.phone.trim()))) add(ContactProblem.PhoneInvalid)
    }

    private companion object {
        val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
        val PHONE = Regex("""^\+?[0-9 ()-]{6,20}$""")
    }
}

/**
 * Who a stay or a car is booked for: the traveller's primary passenger (name, email, phone), else
 * the account's name and email. Booking for someone else is just editing the fields.
 */
class PrefillContactUseCase @Inject constructor(
    private val passengers: PassengerRepository,
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(): ContactDetails = coroutineScope {
        val saved = async { (passengers.passengers() as? Outcome.Success)?.value.orEmpty() }
        val profile = async { (profiles.profile() as? Outcome.Success)?.value }
        val primary = saved.await().let { list -> list.firstOrNull { it.isPrimary } ?: list.firstOrNull() }
        val account = profile.await()
        ContactDetails(
            name = primary?.fullName?.takeIf { it.isNotBlank() } ?: account?.fullName.orEmpty(),
            email = primary?.email?.takeIf { it.isNotBlank() } ?: account?.email.orEmpty(),
            phone = primary?.phone.orEmpty(),
        )
    }
}

/** One tab of My trips: what's coming up (soonest first) and what's done or cancelled (latest first). */
data class TripSection<T>(val upcoming: List<T>, val past: List<T>) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()
    val count: Int get() = upcoming.size + past.size
}

data class MyTrips(
    val flights: TripSection<FlightBooking>,
    val stays: TripSection<StayBooking>,
    val cars: TripSection<CarBooking>,
)

/** Loads the three lists together; fails only if all three fail. */
class GetMyTripsUseCase @Inject constructor(
    private val trips: MyTripsRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Outcome<MyTrips, AppError> = coroutineScope {
        val flights = async { trips.flights() }
        val stays = async { trips.stays() }
        val cars = async { trips.cars() }
        val errors = listOf(flights.await(), stays.await(), cars.await()).map { (it as? Outcome.Failure)?.error }
        if (errors.all { it != null }) return@coroutineScope Outcome.Failure(errors.first()!!)
        val today = LocalDate.now(clock)
        val now = LocalDateTime.now(clock)
        Outcome.Success(
            MyTrips(
                flights = split(flights.await().orEmpty(), { it.trip.departure }) { !it.trip.departure.toLocalDate().isBefore(today) },
                stays = split(stays.await().orEmpty(), { it.checkIn.atStartOfDay() }) {
                    it.status == ReservationStatus.Confirmed && !it.checkOut.isBefore(today)
                },
                cars = split(cars.await().orEmpty(), { it.pickupAt }) {
                    it.status == ReservationStatus.Confirmed && it.dropoffAt.isAfter(now)
                },
            ),
        )
    }

    private fun <T> split(items: List<T>, start: (T) -> LocalDateTime, isUpcoming: (T) -> Boolean): TripSection<T> {
        val (upcoming, past) = items.partition(isUpcoming)
        return TripSection(upcoming.sortedBy(start), past.sortedByDescending(start))
    }

    private fun <T> Outcome<List<T>, AppError>.orEmpty(): List<T> = (this as? Outcome.Success)?.value.orEmpty()
}

class CancelReservationUseCase @Inject constructor(private val trips: MyTripsRepository) {
    suspend operator fun invoke(reference: String): Outcome<Unit, CancelError> = trips.cancel(reference)
}

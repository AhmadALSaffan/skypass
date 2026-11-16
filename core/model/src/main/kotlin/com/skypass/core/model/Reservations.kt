package com.skypass.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Who a stay or a car is booked for. Prefilled from the traveller's saved passenger. */
data class ContactDetails(val name: String = "", val email: String = "", val phone: String = "")

/** When the guest expects to arrive at the hotel, so reception can plan. */
enum class ArrivalWindow(val label: String, val apiValue: String) {
    BeforeNoon("Before 12:00", "before_12"),
    Afternoon("12:00–15:00", "12_15"),
    Evening("15:00–18:00", "15_18"),
    Late("After 18:00", "after_18");

    companion object {
        fun fromApi(value: String?): ArrivalWindow? = entries.firstOrNull { it.apiValue == value }
    }
}

/** Requests the hotel tries to honour. They are never guaranteed and never cost extra. */
enum class StayRequest(val label: String, val apiValue: String) {
    HighFloor("High floor", "high_floor"),
    QuietRoom("Quiet room", "quiet_room"),
    EarlyCheckIn("Early check-in", "early_check_in"),
    LateCheckOut("Late check-out", "late_check_out"),
    TwinBeds("Twin beds", "twin_beds"),
    BabyCot("Baby cot", "baby_cot");

    companion object {
        fun fromApi(value: String): StayRequest? = entries.firstOrNull { it.apiValue == value }
    }
}

/** What the guest adds on the booking step of a stay. */
data class StayDetails(
    val guest: ContactDetails,
    val arrival: ArrivalWindow = ArrivalWindow.Evening,
    val requests: Set<StayRequest> = emptySet(),
    val note: String = "",
) {
    companion object {
        const val MAX_NOTE = 500
        const val CHECK_IN_FROM = "15:00"
        const val CHECK_OUT_UNTIL = "11:00"
    }
}

/** Optional car extras, priced per rental day. The server holds the same prices. */
enum class CarExtra(val label: String, val detail: String, val apiValue: String, val perDayUsd: Int) {
    FullCover("Full protection", "No excess on damage or theft", "full_cover", 18),
    ChildSeat("Child seat", "For children 9–18 kg", "child_seat", 8),
    Gps("GPS navigation", "Offline maps, set to English", "gps", 6),
    ExtraDriver("Additional driver", "Share the driving", "extra_driver", 10);

    companion object {
        fun fromApi(value: String): CarExtra? = entries.firstOrNull { it.apiValue == value }
    }
}

/** The main driver's age band. Drivers under 25 pay a daily young driver fee. */
enum class DriverAge(val label: String, val apiValue: String, val feePerDayUsd: Int) {
    Young("21–24", "21_24", 15),
    Adult("25–69", "25_69", 0),
    Senior("70+", "70_plus", 0);

    companion object {
        fun fromApi(value: String?): DriverAge? = entries.firstOrNull { it.apiValue == value }
    }
}

/** What the traveller adds on the booking step of a car. */
data class DriverDetails(
    val driver: ContactDetails,
    val age: DriverAge = DriverAge.Adult,
    /** Lets the desk wait if the flight is late, e.g. "NZ5". */
    val flightNumber: String = "",
    val extras: Set<CarExtra> = emptySet(),
)

enum class ReservationStatus { Confirmed, Cancelled }

/** A stay as kept in the traveller's account, for My trips. */
data class StayBooking(
    val reference: String,
    val hotel: Hotel,
    val room: RoomType,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guests: Int,
    val nightlyUsd: Int,
    val totalUsd: Int,
    val details: StayDetails?,
    val status: ReservationStatus,
    val bookedAt: Instant,
) {
    val nights: Int get() = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut).toInt()

    /** Free cancellation runs until the day before check-in. */
    val freeCancellationUntil: LocalDate? get() = if (hotel.freeCancellation) checkIn.minusDays(1) else null

    fun canCancel(today: LocalDate): Boolean =
        status == ReservationStatus.Confirmed && hotel.freeCancellation && today.isBefore(checkIn)
}

/** A car rental as kept in the traveller's account, for My trips. */
data class CarBooking(
    val reference: String,
    val car: Car,
    val pickupLocation: String,
    val dropoffLocation: String,
    /** The airport's local wall-clock time. */
    val pickupAt: LocalDateTime,
    val dropoffAt: LocalDateTime,
    val days: Int,
    val dailyUsd: Int,
    val extrasUsd: Int,
    val youngDriverFeeUsd: Int,
    val oneWayFeeUsd: Int,
    val totalUsd: Int,
    val details: DriverDetails?,
    val status: ReservationStatus,
    val bookedAt: Instant,
) {
    /** Free cancellation runs until 48 hours before pick-up. */
    val freeCancellationUntil: LocalDateTime? get() = if (car.freeCancellation) pickupAt.minusHours(48) else null

    fun canCancel(now: LocalDateTime): Boolean =
        status == ReservationStatus.Confirmed && car.freeCancellation && now.isBefore(pickupAt.minusHours(48))
}

/** A paid flight in My trips: the trip card's data plus what was paid. */
data class FlightBooking(val trip: UpcomingTrip, val totalUsd: Int, val passengers: Int)

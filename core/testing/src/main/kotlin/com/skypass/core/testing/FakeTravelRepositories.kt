package com.skypass.core.testing

import com.skypass.core.domain.repository.AirportRepository
import com.skypass.core.domain.repository.DestinationRepository
import com.skypass.core.domain.repository.ProfileRepository
import com.skypass.core.domain.repository.TripRepository
import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.Destination
import com.skypass.core.model.Outcome
import com.skypass.core.model.UpcomingTrip
import com.skypass.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map

object TestAirports {
    val JFK = Airport("JFK", "New York", "United States", "John F. Kennedy International", 40.6413, -73.7781)
    val LAX = Airport("LAX", "Los Angeles", "United States", "Los Angeles International", 33.9416, -118.4085)
    val AKL = Airport("AKL", "Auckland", "New Zealand", "Auckland Airport", -37.0082, 174.7850)
    val LHR = Airport("LHR", "London", "United Kingdom", "Heathrow", 51.4700, -0.4543)
    val ZQN = Airport("ZQN", "Queenstown", "New Zealand", "Queenstown Airport", -45.0211, 168.7392)
    val all = listOf(JFK, LAX, AKL, LHR, ZQN)
}

class FakeAirportRepository(var result: Outcome<List<Airport>, AppError> = Outcome.Success(TestAirports.all)) : AirportRepository {
    override suspend fun airports() = result
}

class FakeDestinationRepository(
    var popular: Map<String, List<Destination>> = emptyMap(),
    var featured: List<Destination> = emptyList(),
) : DestinationRepository {
    override suspend fun popularFrom(airportCode: String): Outcome<List<Destination>, AppError> =
        Outcome.Success(popular[airportCode].orEmpty())

    override suspend fun featured(): Outcome<List<Destination>, AppError> = Outcome.Success(featured)
}

class FakeTripRepository : TripRepository {
    /** Unset until a test emits; null means "no trip". */
    private val trip = MutableStateFlow<Any?>(Unset)
    fun emit(value: UpcomingTrip?) {
        trip.value = value
    }

    override fun upcomingTrip(): Flow<UpcomingTrip?> = trip.filterNot { it === Unset }.map { it as UpcomingTrip? }

    private object Unset
}

class FakeProfileRepository(
    var result: Outcome<UserProfile, AppError> = Outcome.Success(
        UserProfile("u-1", "Tina Anderson", "tina.anderson@gmail.com", "Blue", "JFK", 0),
    ),
) : ProfileRepository {
    override suspend fun profile() = result
}

class FakeLocationRepository(var location: com.skypass.core.model.GeoPoint? = null) : com.skypass.core.domain.repository.LocationRepository {
    override fun hasPermission(): Boolean = location != null
    override suspend fun currentLocation(): com.skypass.core.model.GeoPoint? = location
}

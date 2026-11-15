package com.skypass.core.domain.usecase

import com.skypass.core.model.Cabin
import com.skypass.core.model.Destination
import com.skypass.core.model.Outcome
import com.skypass.core.model.TripType
import com.skypass.core.testing.FakeDestinationRepository
import com.skypass.core.testing.TestAirports
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HomeUseCasesTest {

    private fun clockAt(iso: String) = Clock.fixed(Instant.parse(iso), ZoneId.of("UTC"))

    @Test
    fun `greeting follows the time of day`() {
        assertEquals(DayPart.Morning, GreetingUseCase(clockAt("2026-11-15T08:00:00Z"))())
        assertEquals(DayPart.Afternoon, GreetingUseCase(clockAt("2026-11-15T14:00:00Z"))())
        assertEquals(DayPart.Evening, GreetingUseCase(clockAt("2026-11-15T21:00:00Z"))())
        assertEquals(DayPart.Evening, GreetingUseCase(clockAt("2026-11-15T02:00:00Z"))())
    }

    @Test
    fun `default search departs in five days, one adult, economy, one way`() {
        val query = CreateDefaultSearchUseCase(clockAt("2026-11-15T10:00:00Z"))(TestAirports.JFK, TestAirports.LAX)
        assertEquals(LocalDate.of(2026, 11, 20), query.departDate)
        assertEquals(1, query.adults)
        assertEquals(Cabin.Economy, query.cabin)
        assertEquals(TripType.OneWay, query.tripType)
        assertTrue(query.isValid)
    }

    @Test
    fun `airport search ranks exact code, then prefixes, then contains`() {
        val search = SearchAirportsUseCase()
        assertEquals("AKL", search(TestAirports.all, "akl").first().code)
        assertEquals(listOf("LHR"), search(TestAirports.all, "Lond").map { it.code })
        assertEquals(setOf("AKL", "ZQN"), search(TestAirports.all, "zealand").map { it.code }.toSet())
        assertEquals(TestAirports.all.size, search(TestAirports.all, " ").size)
    }

    @Test
    fun `popular falls back to featured when the airport has no list`() = runTest {
        val lisbon = Destination("1", "Lisbon", "Portugal", "LIS", "Cities", 412, 0xFFCDEBE6)
        val queenstown = Destination("2", "Queenstown", "New Zealand", "ZQN", "Mountains", 689, 0xFFCDEBE6)
        val useCase = GetPopularDestinationsUseCase(
            FakeDestinationRepository(popular = mapOf("JFK" to listOf(lisbon)), featured = listOf(queenstown)),
        )
        assertEquals(Outcome.Success(listOf(lisbon)), useCase("JFK"))
        assertEquals(Outcome.Success(listOf(queenstown)), useCase("AKL"))
    }

    @Test
    fun `nearest airport by great-circle distance`() {
        val nearest = FindNearestAirportUseCase.nearest(com.skypass.core.model.GeoPoint(51.5, -0.12), TestAirports.all)
        assertEquals("LHR", nearest?.code)
        // Mountain View, CA (the emulator's default location) → LAX among these.
        assertEquals("LAX", FindNearestAirportUseCase.nearest(com.skypass.core.model.GeoPoint(37.42, -122.08), TestAirports.all)?.code)
    }
}

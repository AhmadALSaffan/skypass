package com.skypass.core.domain.usecase

import com.skypass.core.model.DepartureWindow
import com.skypass.core.model.FlightFilters
import com.skypass.core.model.SortOrder
import com.skypass.core.testing.TestFlights
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResultsUseCasesTest {

    private val sort = SortFlightsUseCase()
    private val filter = FilterFlightsUseCase()

    @Test
    fun `cheapest matches the board order`() {
        assertEquals(listOf("NZ 5", "UA 2270", "AS 1235", "B6 23", "AA 1"), sort(TestFlights.all, SortOrder.Cheapest).map { it.flightNumber })
    }

    @Test
    fun `fastest puts the shortest flight first`() {
        assertEquals("B6 23", sort(TestFlights.all, SortOrder.Fastest).first().flightNumber)
        assertEquals("UA 2270", sort(TestFlights.all, SortOrder.Fastest).last().flightNumber)
    }

    @Test
    fun `earliest follows departure time`() {
        assertEquals(listOf("UA 2270", "NZ 5", "B6 23", "AA 1", "AS 1235"), sort(TestFlights.all, SortOrder.Earliest).map { it.flightNumber })
    }

    @Test
    fun `filters combine nonstop, airline and time of day`() {
        assertEquals(3, filter(TestFlights.all, FlightFilters(nonstopOnly = true)).size)
        assertEquals(listOf("NZ 5"), filter(TestFlights.all, FlightFilters(airlines = setOf("NZ"))).map { it.flightNumber })
        assertEquals(
            listOf("AS 1235", "AA 1"), // filtering keeps the incoming order
            filter(TestFlights.all, FlightFilters(windows = setOf(DepartureWindow.Afternoon))).map { it.flightNumber },
        )
        assertEquals(
            listOf("NZ 5", "B6 23"),
            filter(TestFlights.all, FlightFilters(nonstopOnly = true, windows = setOf(DepartureWindow.Morning))).map { it.flightNumber },
        )
    }
}

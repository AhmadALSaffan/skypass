package com.skypass.core.domain.usecase

import com.skypass.core.model.AppError
import com.skypass.core.model.ExploreCategory
import com.skypass.core.model.Outcome
import com.skypass.core.testing.FakeExploreRepository
import com.skypass.core.testing.FakeLocationRepository
import com.skypass.core.testing.FakeProfileRepository
import com.skypass.core.testing.TestAirports
import com.skypass.core.testing.TestExplore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExploreUseCasesTest {

    private val filter = FilterDestinationsUseCase()

    @Test
    fun `a category chip keeps only its destinations, All keeps everything`() {
        assertEquals(listOf("Tulum"), filter(TestExplore.all, ExploreCategory.Beaches, "").map { it.city })
        assertEquals(TestExplore.all, filter(TestExplore.all, ExploreCategory.All, " "))
    }

    @Test
    fun `search matches city, country or exact airport code`() {
        assertEquals(listOf("Queenstown"), filter(TestExplore.all, ExploreCategory.All, "queens").map { it.city })
        assertEquals(listOf("Tulum"), filter(TestExplore.all, ExploreCategory.All, "MEXICO").map { it.city })
        assertEquals(listOf("London"), filter(TestExplore.all, ExploreCategory.All, "lhr").map { it.city })
        // Codes must match whole: "LH" finds nothing, not London.
        assertEquals(emptyList<String>(), filter(TestExplore.all, ExploreCategory.All, "LH").map { it.city })
    }

    @Test
    fun `a missing deal doesn't hide the grid, a failed grid is an error`() = runTest {
        val repo = FakeExploreRepository(deals = emptyList())
        val ok = GetExploreCatalogUseCase(repo)("AKL") as Outcome.Success
        assertNull(ok.value.deal)
        assertEquals(TestExplore.all.size, ok.value.destinations.size)

        repo.failure = AppError.Network
        assertEquals(Outcome.Failure(AppError.Network), GetExploreCatalogUseCase(repo)("AKL"))
    }

    @Test
    fun `origin is the nearest airport, else the profile's home`() = runTest {
        val location = FakeLocationRepository()
        val resolve = ResolveOriginAirportUseCase(FindNearestAirportUseCase(location), FakeProfileRepository())
        assertEquals("JFK", resolve(TestAirports.all)?.code)
        location.location = com.skypass.core.model.GeoPoint(51.5, -0.12) // London
        assertEquals("LHR", resolve(TestAirports.all)?.code)
    }
}

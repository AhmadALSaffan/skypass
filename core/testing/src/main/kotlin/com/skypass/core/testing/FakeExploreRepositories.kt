package com.skypass.core.testing

import com.skypass.core.domain.repository.ExploreRepository
import com.skypass.core.domain.repository.SearchIntentRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Deal
import com.skypass.core.model.Destination
import com.skypass.core.model.Outcome
import com.skypass.core.model.SearchIntent
import kotlinx.coroutines.flow.MutableStateFlow

object TestExplore {
    val QUEENSTOWN = Destination("d-1", "Queenstown", "New Zealand", "ZQN", "Mountains", 108, 0xFFCDEBE6, pricedFromOrigin = true)
    val LISBON = Destination("d-2", "Lisbon", "Portugal", "LIS", "Cities", 412, 0xFFF7EBC4)
    val BORA_BORA = Destination("d-3", "Bora Bora", "French Polynesia", "BOB", "Islands", 1240, 0xFFCFE0F2)
    val TULUM = Destination("d-4", "Tulum", "Mexico", "TQO", "Beaches", 356, 0xFFF9DCD2)
    val LONDON = Destination("d-5", "London", "United Kingdom", "LHR", "Cities", 1416, 0xFFCFE0F2, pricedFromOrigin = true)
    val all = listOf(QUEENSTOWN, LISBON, BORA_BORA, TULUM, LONDON)

    val AOTEAROA = Deal("deal-1", "Fly to Aotearoa this summer", "NZ", "JFK", "AKL", nonstop = true, fromPriceUsd = 899, tone = 0xFFCFE0F2)
}

class FakeExploreRepository(
    var destinations: List<Destination> = TestExplore.all,
    var deals: List<Deal> = listOf(TestExplore.AOTEAROA),
    var failure: AppError? = null,
) : ExploreRepository {
    val requestedOrigins = mutableListOf<String>()

    override suspend fun destinations(originCode: String): Outcome<List<Destination>, AppError> {
        requestedOrigins += originCode
        return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(destinations.filter { it.airportCode != originCode })
    }

    override suspend fun deals(): Outcome<List<Deal>, AppError> = Outcome.Success(deals)
}

class FakeSearchIntentRepository : SearchIntentRepository {
    override val pending = MutableStateFlow<SearchIntent?>(null)

    override fun request(intent: SearchIntent) {
        pending.value = intent
    }

    override fun consume() {
        pending.value = null
    }
}

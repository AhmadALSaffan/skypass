package com.skypass.core.domain.repository

import com.skypass.core.model.AppError
import com.skypass.core.model.Deal
import com.skypass.core.model.Destination
import com.skypass.core.model.Outcome
import com.skypass.core.model.SearchIntent
import kotlinx.coroutines.flow.StateFlow

interface ExploreRepository {
    /** Featured destinations, priced from [originCode] where Skypass flies from there. */
    suspend fun destinations(originCode: String): Outcome<List<Destination>, AppError>

    /** Active fare deals, best first. */
    suspend fun deals(): Outcome<List<Deal>, AppError>
}

/**
 * Hand-off between tabs: Explore asks for a search, Home picks it up and fills its card. Lives
 * for the process only; it's navigation state, not data.
 */
interface SearchIntentRepository {
    val pending: StateFlow<SearchIntent?>

    fun request(intent: SearchIntent)

    /** Home took the intent; clear it so it isn't applied twice. */
    fun consume()
}

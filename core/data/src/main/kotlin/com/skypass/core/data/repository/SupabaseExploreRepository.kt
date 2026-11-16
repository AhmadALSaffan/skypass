package com.skypass.core.data.repository

import com.skypass.core.data.remote.DealDto
import com.skypass.core.data.remote.ExploreDestinationDto
import com.skypass.core.domain.repository.ExploreRepository
import com.skypass.core.domain.repository.SearchIntentRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Deal
import com.skypass.core.model.Destination
import com.skypass.core.model.Outcome
import com.skypass.core.model.SearchIntent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

internal class SupabaseExploreRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : ExploreRepository {

    override suspend fun destinations(originCode: String): Outcome<List<Destination>, AppError> = supabase.safeCall {
        supabase.postgrest.rpc("explore_destinations", buildJsonObject { put("p_origin", originCode) })
            .decodeList<ExploreDestinationDto>()
            .map { it.toModel() }
    }

    override suspend fun deals(): Outcome<List<Deal>, AppError> = supabase.safeCall {
        supabase.from("deals").select {
            filter { eq("active", true) }
            order("from_price_usd", Order.ASCENDING)
        }.decodeList<DealDto>().map { it.toModel() }
    }
}

@Singleton
internal class InMemorySearchIntentRepository @Inject constructor() : SearchIntentRepository {
    private val state = MutableStateFlow<SearchIntent?>(null)
    override val pending: StateFlow<SearchIntent?> = state.asStateFlow()

    override fun request(intent: SearchIntent) {
        state.value = intent
    }

    override fun consume() {
        state.value = null
    }
}

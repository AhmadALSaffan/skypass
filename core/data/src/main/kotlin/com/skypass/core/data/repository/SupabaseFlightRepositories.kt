package com.skypass.core.data.repository

import com.skypass.core.data.remote.DayPriceDto
import com.skypass.core.data.remote.FlightOfferDto
import com.skypass.core.data.remote.SavedItemDto
import com.skypass.core.domain.repository.FlightSearchRepository
import com.skypass.core.domain.repository.SavedFlightsRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Cabin
import com.skypass.core.model.DayPrice
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.Outcome
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

internal class SupabaseFlightSearchRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : FlightSearchRepository {

    override suspend fun search(
        from: String,
        to: String,
        date: LocalDate,
        cabin: Cabin,
        passengers: Int,
    ): Outcome<List<FlightOffer>, AppError> = safeCall {
        supabase.postgrest.rpc(
            "search_flights",
            buildJsonObject {
                put("p_origin", from)
                put("p_destination", to)
                put("p_date", date.toString())
                put("p_cabin", cabin.apiValue)
                put("p_passengers", passengers)
            },
        ).decodeList<FlightOfferDto>().map { it.toModel() }
    }

    override suspend fun priceStrip(from: String, to: String, center: LocalDate, cabin: Cabin): Outcome<List<DayPrice>, AppError> =
        safeCall {
            supabase.postgrest.rpc(
                "date_price_strip",
                buildJsonObject {
                    put("p_origin", from)
                    put("p_destination", to)
                    put("p_center", center.toString())
                    put("p_cabin", cabin.apiValue)
                },
            ).decodeList<DayPriceDto>().map { it.toModel() }
        }
}

/**
 * Saved departures, kept in memory for instant bookmark feedback and written through to
 * `saved_items`. Reloads when a different user signs in.
 */
@Singleton
internal class SupabaseSavedFlightsRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : SavedFlightsRepository {

    private val ids = MutableStateFlow<Set<String>>(emptySet())
    private var loadedFor: String? = null

    override fun savedFlightIds(): Flow<Set<String>> = flow {
        val user = supabase.auth.currentUserOrNull()?.id
        if (user != null && user != loadedFor) {
            val loaded = safeCall {
                supabase.from("saved_items").select { filter { eq("kind", KIND) } }
                    .decodeList<SavedItemDto>()
                    .map { it.itemId }
                    .toSet()
            }
            if (loaded is Outcome.Success) {
                ids.value = loaded.value
                loadedFor = user
            }
        }
        emitAll(ids)
    }

    override suspend fun setSaved(instanceId: String, saved: Boolean): Outcome<Unit, AppError> {
        // Optimistic: the bookmark fills immediately; roll back if the server refuses.
        ids.update { if (saved) it + instanceId else it - instanceId }
        val result = safeCall {
            if (saved) {
                supabase.from("saved_items").upsert(SavedItemDto(KIND, instanceId))
            } else {
                supabase.from("saved_items").delete {
                    filter {
                        eq("kind", KIND)
                        eq("item_id", instanceId)
                    }
                }
            }
            Unit
        }
        if (result is Outcome.Failure) ids.update { if (saved) it - instanceId else it + instanceId }
        return result
    }

    private companion object {
        const val KIND = "flight"
    }
}

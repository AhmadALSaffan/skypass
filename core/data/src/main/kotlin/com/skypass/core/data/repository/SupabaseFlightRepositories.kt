package com.skypass.core.data.repository

import com.skypass.core.data.remote.DayPriceDto
import com.skypass.core.data.remote.FlightOfferDto
import com.skypass.core.domain.repository.FlightSearchRepository
import com.skypass.core.domain.repository.SavedFlightsRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Cabin
import com.skypass.core.model.DayPrice
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.Outcome
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
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
    ): Outcome<List<FlightOffer>, AppError> = supabase.safeCall {
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
        supabase.safeCall {
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

/** Saved departures (results bookmark, Saved tab). One store per process so every screen agrees. */
@Singleton
internal class SupabaseSavedFlightsRepository @Inject constructor(
    supabase: SupabaseClient,
) : SavedFlightsRepository {
    private val store = SavedItemsStore(supabase, "flight")

    override fun savedFlightIds(): Flow<Set<String>> = store.ids()

    override suspend fun setSaved(instanceId: String, saved: Boolean): Outcome<Unit, AppError> =
        store.setSaved(instanceId, saved)
}

package com.skypass.core.data.repository

import android.util.Log
import com.skypass.core.data.remote.TripDto
import com.skypass.core.datastore.SkypassPreferences
import com.skypass.core.domain.repository.TripRepository
import com.skypass.core.model.Outcome
import com.skypass.core.model.UpcomingTrip
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * Offline-first upcoming trip:
 * 1. emit the trip cached on the phone (instant, works offline),
 * 2. fetch `next_trip()` and emit + cache it,
 * 3. subscribe to Realtime updates of that departure and emit gate / status changes live.
 */
internal class SupabaseTripRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val preferences: SkypassPreferences,
) : TripRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun upcomingTrip(): Flow<UpcomingTrip?> = channelFlow {
        val cached = readCache()
        if (cached != null) send(cached.toModel())

        val fresh = safeCall { supabase.postgrest.rpc("next_trip").decodeList<TripDto>().firstOrNull() }
        if (fresh !is Outcome.Success) {
            // Offline or refused: keep showing the cached trip (or nothing) and stop here.
            if (cached == null) send(null)
            return@channelFlow
        }
        var trip = fresh.value
        writeCache(trip)
        send(trip?.toModel())
        if (trip == null) return@channelFlow

        val channel = supabase.channel("trip-${trip.instanceId}")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "flight_instances"
            filter("id", FilterOperator.EQ, trip!!.instanceId)
        }
        try {
            channel.subscribe()
            changes.collect { update ->
                val record = update.record
                trip = trip!!.copy(
                    gate = record["gate"]?.jsonPrimitive?.contentOrNull ?: trip!!.gate,
                    status = record["status"]?.jsonPrimitive?.contentOrNull ?: trip!!.status,
                    delayMinutes = record["delay_minutes"]?.jsonPrimitive?.intOrNull ?: trip!!.delayMinutes,
                )
                writeCache(trip)
                send(trip!!.toModel())
            }
        } finally {
            withContext(NonCancellable) { runCatching { supabase.realtime.removeChannel(channel) } }
        }
    }.distinctUntilChanged()

    private suspend fun readCache(): TripDto? = preferences.cachedUpcomingTrip.first()?.let {
        runCatching { json.decodeFromString<TripDto>(it) }
            .onFailure { e -> Log.w("Skypass", "Dropping unreadable trip cache", e) }
            .getOrNull()
    }

    private suspend fun writeCache(trip: TripDto?) {
        preferences.setCachedUpcomingTrip(trip?.let { json.encodeToString(TripDto.serializer(), it) })
    }
}

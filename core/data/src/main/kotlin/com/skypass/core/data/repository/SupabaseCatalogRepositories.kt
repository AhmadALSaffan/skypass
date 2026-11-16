package com.skypass.core.data.repository

import com.skypass.core.data.remote.AirportDto
import com.skypass.core.data.remote.DestinationDto
import com.skypass.core.data.remote.ProfileDto
import com.skypass.core.domain.repository.AirportRepository
import com.skypass.core.domain.repository.DestinationRepository
import com.skypass.core.domain.repository.ProfileRepository
import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.Destination
import com.skypass.core.model.Outcome
import com.skypass.core.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Airports rarely change: loaded once per process. */
@Singleton
internal class SupabaseAirportRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : AirportRepository {
    private val lock = Mutex()
    private var cache: List<Airport>? = null

    override suspend fun airports(): Outcome<List<Airport>, AppError> = lock.withLock {
        cache?.let { return Outcome.Success(it) }
        supabase.safeCall {
            supabase.from("airports").select().decodeList<AirportDto>().map { it.toModel() }
        }.also { if (it is Outcome.Success) cache = it.value }
    }
}

internal class SupabaseDestinationRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : DestinationRepository {

    override suspend fun popularFrom(airportCode: String): Outcome<List<Destination>, AppError> = supabase.safeCall {
        supabase.from("destinations").select {
            filter { eq("popular_from", airportCode) }
            order("sort_order", Order.ASCENDING)
        }.decodeList<DestinationDto>().map { it.toModel() }
    }

    override suspend fun featured(): Outcome<List<Destination>, AppError> = supabase.safeCall {
        supabase.from("destinations").select {
            filter { exact("popular_from", null) }
            order("sort_order", Order.ASCENDING)
        }.decodeList<DestinationDto>().map { it.toModel() }
    }
}

internal class SupabaseProfileRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : ProfileRepository {

    override suspend fun profile(): Outcome<UserProfile, AppError> {
        val userId = supabase.signedInUserId() ?: return Outcome.Failure(AppError.Unauthorized)
        return supabase.safeCall {
            supabase.from("profiles").select { filter { eq("id", userId) } }
                .decodeSingle<ProfileDto>()
                .toModel()
        }
    }
}

package com.skypass.core.data.repository

import com.skypass.core.data.remote.ProfileDto
import com.skypass.core.data.remote.ProfileStatsDto
import com.skypass.core.data.remote.ProfileUpdateDto
import com.skypass.core.data.remote.SavedDestinationDto
import com.skypass.core.data.remote.SavedFlightDto
import com.skypass.core.data.remote.SavedHotelDto
import com.skypass.core.domain.repository.EditProfileRepository
import com.skypass.core.domain.repository.ProfileStatsRepository
import com.skypass.core.domain.repository.SavedDestinationsRepository
import com.skypass.core.domain.repository.SavedListRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Outcome
import com.skypass.core.model.ProfileStats
import com.skypass.core.model.SavedDestination
import com.skypass.core.model.SavedFlight
import com.skypass.core.model.SavedHotel
import com.skypass.core.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

internal class SupabaseProfileStatsRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : ProfileStatsRepository {
    override suspend fun stats(): Outcome<ProfileStats, AppError> = supabase.safeCall {
        supabase.postgrest.rpc("profile_stats").decodeList<ProfileStatsDto>().single().toModel()
    }
}

/** Column privileges on `profiles` only let the owner change these two fields. */
internal class SupabaseEditProfileRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : EditProfileRepository {
    override suspend fun update(fullName: String, homeAirport: String): Outcome<UserProfile, AppError> {
        val userId = supabase.signedInUserId() ?: return Outcome.Failure(AppError.Unauthorized)
        return supabase.safeCall {
            supabase.from("profiles").update(ProfileUpdateDto(fullName, homeAirport)) {
                filter { eq("id", userId) }
                select()
            }.decodeSingle<ProfileDto>().toModel()
        }
    }

    override suspend fun updateHomeAirport(homeAirport: String): Outcome<UserProfile, AppError> {
        val userId = supabase.signedInUserId() ?: return Outcome.Failure(AppError.Unauthorized)
        return supabase.safeCall {
            supabase.from("profiles").update(HomeAirportDto(homeAirport)) {
                filter { eq("id", userId) }
                select()
            }.decodeSingle<ProfileDto>().toModel()
        }
    }
}

@kotlinx.serialization.Serializable
private data class HomeAirportDto(@kotlinx.serialization.SerialName("home_airport") val homeAirport: String)

internal class SupabaseSavedListRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : SavedListRepository {

    override suspend fun flights(): Outcome<List<SavedFlight>, AppError> = supabase.safeCall {
        supabase.postgrest.rpc("saved_flights").decodeList<SavedFlightDto>().map { it.toModel() }
    }

    override suspend fun hotels(): Outcome<List<SavedHotel>, AppError> = supabase.safeCall {
        supabase.postgrest.rpc("saved_hotels").decodeList<SavedHotelDto>().map {
            SavedHotel(it.hotel(), OffsetDateTime.parse(it.savedAt).toInstant())
        }
    }

    override suspend fun destinations(originCode: String): Outcome<List<SavedDestination>, AppError> = supabase.safeCall {
        supabase.postgrest.rpc("saved_destinations", buildJsonObject { put("p_origin", originCode) })
            .decodeList<SavedDestinationDto>()
            .map { SavedDestination(it.destination(), OffsetDateTime.parse(it.savedAt).toInstant()) }
    }
}

/** Hearted destinations. One store per process so Explore and the Saved tab agree. */
@Singleton
internal class SupabaseSavedDestinationsRepository @Inject constructor(
    supabase: SupabaseClient,
) : SavedDestinationsRepository {
    private val store = SavedItemsStore(supabase, "destination")

    override fun savedDestinationIds(): Flow<Set<String>> = store.ids()

    override suspend fun setSaved(destinationId: String, saved: Boolean): Outcome<Unit, AppError> = store.setSaved(destinationId, saved)
}

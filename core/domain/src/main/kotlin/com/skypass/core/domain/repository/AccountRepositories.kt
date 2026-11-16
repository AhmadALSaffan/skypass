package com.skypass.core.domain.repository

import com.skypass.core.model.AppError
import com.skypass.core.model.Outcome
import com.skypass.core.model.ProfileStats
import com.skypass.core.model.SavedDestination
import com.skypass.core.model.SavedFlight
import com.skypass.core.model.SavedHotel
import com.skypass.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** The Profile tiles. */
interface ProfileStatsRepository {
    suspend fun stats(): Outcome<ProfileStats, AppError>
}

/** Personal information: the traveller may change their name and home airport, nothing else. */
interface EditProfileRepository {
    suspend fun update(fullName: String, homeAirport: String): Outcome<UserProfile, AppError>
}

/** Everything on the Saved tab, with the details each card needs. */
interface SavedListRepository {
    suspend fun flights(): Outcome<List<SavedFlight>, AppError>

    suspend fun hotels(): Outcome<List<SavedHotel>, AppError>

    /** Priced from [originCode], like Explore. */
    suspend fun destinations(originCode: String): Outcome<List<SavedDestination>, AppError>
}

/** Hearted destinations (Explore grid, Saved tab). */
interface SavedDestinationsRepository {
    fun savedDestinationIds(): Flow<Set<String>>

    suspend fun setSaved(destinationId: String, saved: Boolean): Outcome<Unit, AppError>
}

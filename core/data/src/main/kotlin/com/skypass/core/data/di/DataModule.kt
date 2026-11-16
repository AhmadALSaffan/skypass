package com.skypass.core.data.di

import com.skypass.core.data.repository.AndroidLocationRepository
import com.skypass.core.data.repository.LocalOnboardingRepository
import com.skypass.core.data.repository.SupabaseAirportRepository
import com.skypass.core.data.repository.SupabaseAuthRepository
import com.skypass.core.data.repository.SupabaseBookingRepository
import com.skypass.core.data.repository.SupabaseDestinationRepository
import com.skypass.core.data.repository.SupabaseExploreRepository
import com.skypass.core.data.repository.SupabaseHotelRepository
import com.skypass.core.data.repository.SupabaseSavedHotelsRepository
import com.skypass.core.data.repository.InMemorySearchIntentRepository
import com.skypass.core.data.repository.SupabasePromoRepository
import com.skypass.core.data.repository.SupabaseFlightOfferRepository
import com.skypass.core.data.repository.SupabasePassengerRepository
import com.skypass.core.data.repository.SupabaseSeatMapRepository
import com.skypass.core.data.repository.SupabaseFlightSearchRepository
import com.skypass.core.data.repository.SupabaseSavedFlightsRepository
import com.skypass.core.data.repository.SupabaseProfileRepository
import com.skypass.core.data.repository.SupabaseTripRepository
import com.skypass.core.domain.repository.AirportRepository
import com.skypass.core.domain.repository.AuthRepository
import com.skypass.core.domain.repository.BookingRepository
import com.skypass.core.domain.repository.DestinationRepository
import com.skypass.core.domain.repository.ExploreRepository
import com.skypass.core.domain.repository.HotelRepository
import com.skypass.core.domain.repository.SavedHotelsRepository
import com.skypass.core.domain.repository.SearchIntentRepository
import com.skypass.core.domain.repository.PromoRepository
import com.skypass.core.domain.repository.FlightOfferRepository
import com.skypass.core.domain.repository.PassengerRepository
import com.skypass.core.domain.repository.SeatMapRepository
import com.skypass.core.domain.repository.FlightSearchRepository
import com.skypass.core.domain.repository.SavedFlightsRepository
import com.skypass.core.domain.repository.LocationRepository
import com.skypass.core.domain.repository.OnboardingRepository
import com.skypass.core.domain.repository.ProfileRepository
import com.skypass.core.domain.repository.TripRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** Dependency inversion: features see interfaces from :core:domain, Hilt binds the implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(impl: LocalOnboardingRepository): OnboardingRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds
    abstract fun bindAirportRepository(impl: SupabaseAirportRepository): AirportRepository

    @Binds
    abstract fun bindDestinationRepository(impl: SupabaseDestinationRepository): DestinationRepository

    @Binds
    abstract fun bindExploreRepository(impl: SupabaseExploreRepository): ExploreRepository

    @Binds
    @Singleton
    abstract fun bindSearchIntentRepository(impl: InMemorySearchIntentRepository): SearchIntentRepository

    @Binds
    abstract fun bindHotelRepository(impl: SupabaseHotelRepository): HotelRepository

    @Binds
    abstract fun bindSavedHotelsRepository(impl: SupabaseSavedHotelsRepository): SavedHotelsRepository

    @Binds
    abstract fun bindTripRepository(impl: SupabaseTripRepository): TripRepository

    @Binds
    abstract fun bindFlightSearchRepository(impl: SupabaseFlightSearchRepository): FlightSearchRepository

    @Binds
    abstract fun bindSavedFlightsRepository(impl: SupabaseSavedFlightsRepository): SavedFlightsRepository

    @Binds
    abstract fun bindFlightOfferRepository(impl: SupabaseFlightOfferRepository): FlightOfferRepository

    @Binds
    abstract fun bindSeatMapRepository(impl: SupabaseSeatMapRepository): SeatMapRepository

    @Binds
    abstract fun bindPassengerRepository(impl: SupabasePassengerRepository): PassengerRepository

    @Binds
    abstract fun bindPromoRepository(impl: SupabasePromoRepository): PromoRepository

    @Binds
    abstract fun bindBookingRepository(impl: SupabaseBookingRepository): BookingRepository

    @Binds
    abstract fun bindLocationRepository(impl: AndroidLocationRepository): LocationRepository

    @Binds
    abstract fun bindProfileRepository(impl: SupabaseProfileRepository): ProfileRepository

    companion object {
        /** The phone's clock; tests pass a fixed one. */
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}

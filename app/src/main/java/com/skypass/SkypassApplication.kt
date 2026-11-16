package com.skypass

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.skypass.core.domain.repository.AuthRepository
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class SkypassApplication : Application(), SingletonImageLoader.Factory {

    /** Lazy so it is built on the warm-up thread below, not on the main thread. */
    @Inject lateinit var auth: Lazy<AuthRepository>

    override fun onCreate() {
        super.onCreate()
        // Build the Supabase client (Ktor, serialization, Auth loading the saved session) while the
        // activity is still being created, so the splash screen has nothing left to wait for.
        Thread({ auth.get() }, "supabase-warm-up").start()
        createNotificationChannels()
    }

    /** One channel per Settings switch, so travellers can also tune them in system settings. */
    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        listOf(
            Triple(CHANNEL_TRIPS, R.string.channel_trips, R.string.channel_trips_description) to NotificationManager.IMPORTANCE_DEFAULT,
            Triple(CHANNEL_GATE, R.string.channel_gate, R.string.channel_gate_description) to NotificationManager.IMPORTANCE_HIGH,
            Triple(CHANNEL_PRICES, R.string.channel_prices, R.string.channel_prices_description) to NotificationManager.IMPORTANCE_LOW,
        ).forEach { (ids, importance) ->
            val (id, name, description) = ids
            manager.createNotificationChannel(
                NotificationChannel(id, getString(name), importance).apply { this.description = getString(description) },
            )
        }
    }

    /**
     * One image loader for the app. Photo hosts such as Wikimedia refuse requests with a generic
     * client User-Agent (HTTP 403), so every image request identifies Skypass.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .crossfade(true)
            .build()
    }

    companion object {
        const val CHANNEL_TRIPS = "trip_updates"
        const val CHANNEL_GATE = "gate_alerts"
        const val CHANNEL_PRICES = "price_drops"
        private const val USER_AGENT = "Skypass/${BuildConfig.VERSION_NAME} (Android; portfolio demo app)"
    }
}

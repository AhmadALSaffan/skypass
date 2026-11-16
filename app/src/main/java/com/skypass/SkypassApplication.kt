package com.skypass

import android.app.Application
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

    private companion object {
        const val USER_AGENT = "Skypass/${BuildConfig.VERSION_NAME} (Android; portfolio demo app)"
    }
}

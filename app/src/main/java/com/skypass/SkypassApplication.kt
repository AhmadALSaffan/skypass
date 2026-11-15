package com.skypass

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient

@HiltAndroidApp
class SkypassApplication : Application(), SingletonImageLoader.Factory {

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

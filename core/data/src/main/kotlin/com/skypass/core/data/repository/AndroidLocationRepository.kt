package com.skypass.core.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.skypass.core.domain.repository.LocationRepository
import com.skypass.core.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Coarse location from the platform LocationManager (no Play Services needed). City-level
 * accuracy is plenty to pick the nearest airport.
 */
internal class AndroidLocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationRepository {

    private val manager get() = context.getSystemService(LocationManager::class.java)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Checked by hasPermission().
    override suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        val manager = manager ?: return null
        val providers = manager.getProviders(true)

        // A fix from the last 30 minutes is good enough for "which airport is near me".
        val recent = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < RECENT_MS }
            .maxByOrNull { it.time }
        if (recent != null) return recent.toPoint()

        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> null
        }
        val fresh = if (provider != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                }
            }
        } else {
            null
        }
        return (fresh ?: providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time })
            ?.toPoint()
    }

    private fun Location.toPoint() = GeoPoint(latitude, longitude)

    private companion object {
        const val RECENT_MS = 30 * 60 * 1000L
        const val TIMEOUT_MS = 5_000L
    }
}

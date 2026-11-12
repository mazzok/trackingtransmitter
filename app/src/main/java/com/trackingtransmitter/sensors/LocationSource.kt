package com.trackingtransmitter.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import com.trackingtransmitter.domain.Fix
import com.trackingtransmitter.domain.LatLon
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * Location fixes every ~1 s. Uses the platform fused provider (GPS plus Wi-Fi/cell, no Play Services needed)
 * so the first fix comes quickly, also indoors; falls back to the GPS chip.
 */
class LocationSource(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission") // permission is checked before searching starts
    fun fixes(): Flow<Fix> = callbackFlow {
        val listener = LocationListener {
            trySend(Fix(LatLon(it.latitude, it.longitude), if (it.hasAccuracy()) it.accuracy else null))
        }
        val request = LocationRequest.Builder(MIN_TIME_MS).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build()
        manager.requestLocationUpdates(providerOf(manager), request, context.mainExecutor, listener)
        awaitClose { manager.removeUpdates(listener) }
    }.buffer(64)

    @SuppressLint("MissingPermission")
    fun lastKnown(): LatLon? = try {
        manager.getLastKnownLocation(providerOf(manager))?.let { LatLon(it.latitude, it.longitude) }
    } catch (e: SecurityException) {
        null
    }

    companion object {
        const val MIN_TIME_MS = 1_000L

        /** The fused provider where the phone has one, otherwise the GPS chip. */
        fun providerOf(manager: LocationManager): String =
            if (manager.hasProvider(LocationManager.FUSED_PROVIDER)) LocationManager.FUSED_PROVIDER
            else LocationManager.GPS_PROVIDER
    }
}

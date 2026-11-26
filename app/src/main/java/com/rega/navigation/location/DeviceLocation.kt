package com.rega.navigation.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

class DeviceLocation(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun lastKnown(): Location? {
        if (!hasPermission()) return null
        return runCatching {
            manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    @Suppress("MissingPermission")
    fun updates(listener: (Location) -> Unit): LocationListener? {
        if (!hasPermission()) return null
        val enabledProviders = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        if (enabledProviders.isEmpty()) return null
        val callback = object : LocationListener {
            override fun onLocationChanged(location: Location) = listener(location)
            @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        var requested = false
        enabledProviders.forEach { provider ->
            requested = runCatching {
                manager.requestLocationUpdates(provider, 2500L, 5f, callback)
                true
            }.getOrDefault(false) || requested
        }
        return callback.takeIf { requested }
    }

    fun stop(listener: LocationListener?) { if (listener != null) runCatching { manager.removeUpdates(listener) } }
}

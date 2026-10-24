package com.kurtz.homerecord.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kurtz.homerecord.core.model.GeoFix
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One current-location read, foreground only (AC-F8). Behind an interface so the clock logic runs without a device. */
interface LocationProvider {
    /** True when fine location is granted to the app right now. */
    fun hasPermission(): Boolean

    /** The best fix found within [timeoutMs], or null when there is none (no permission, no signal, timeout). */
    suspend fun currentFix(timeoutMs: Long): GeoFix?
}

/** [LocationProvider] over FusedLocationProvider with a high-accuracy request. */
class FusedLocationProvider(private val context: Context) : LocationProvider {
    override fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentFix(timeoutMs: Long): GeoFix? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancel = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
                        .addOnSuccessListener { loc -> cont.resume(loc?.let { GeoFix(it.latitude, it.longitude, it.accuracy) }) }
                        .addOnFailureListener { cont.resume(null) }
                    cont.invokeOnCancellation { cancel.cancel() }
                }
            }
        } catch (_: SecurityException) {
            null
        }
    }
}

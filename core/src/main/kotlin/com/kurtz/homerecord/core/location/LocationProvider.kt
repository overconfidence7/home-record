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
    /** True when precise or approximate location is granted to the app right now (Android 12+ lets people pick approximate). */
    fun hasPermission(): Boolean

    /** The best fix found within [timeoutMs], or null when there is none (no permission, no signal, timeout). */
    suspend fun currentFix(timeoutMs: Long): GeoFix?
}

/** [LocationProvider] over FusedLocationProvider with a high-accuracy request. */
class FusedLocationProvider(private val context: Context) : LocationProvider {
    override fun hasPermission(): Boolean = LocationPermissions.ALL.any { granted(it) }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentFix(timeoutMs: Long): GeoFix? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        // An approximate-only grant cannot ask for high accuracy; balanced still returns the coarse fix.
        val priority = if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val cancel = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    client.getCurrentLocation(priority, cancel.token)
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

/**
 * The location request for a first clock-in (AC-F8). On Android 12 and later the system drops a request for FINE alone
 * with no dialog, so both are asked together, and an approximate grant counts as allowed.
 */
object LocationPermissions {
    val ALL: Array<String> = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** The answer from `RequestMultiplePermissions`: allowed when either precise or approximate was granted. */
    fun allowed(results: Map<String, Boolean>): Boolean = ALL.any { results[it] == true }
}

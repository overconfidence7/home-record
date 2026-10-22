package com.kurtz.homerecord.core.geo

import android.content.Context
import android.location.Geocoder as PlatformGeocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class GeoPoint(val lat: Double, val lng: Double)

/** Turns a street address into coordinates; null when it cannot be resolved now (offline, unknown address). */
interface Geocoder {
    suspend fun resolve(address: String): GeoPoint?
}

/** [Geocoder] over the platform Geocoder, which needs a network and a backing service. */
class AndroidGeocoder(private val context: Context) : Geocoder {
    override suspend fun resolve(address: String): GeoPoint? {
        if (!PlatformGeocoder.isPresent()) return null
        val platform = PlatformGeocoder(context, Locale.getDefault())
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    platform.getFromLocationName(address, 1, object : PlatformGeocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            cont.resume(addresses.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) })
                        }

                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    platform.getFromLocationName(address, 1)?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
                }
            }
        }.getOrNull()
    }
}

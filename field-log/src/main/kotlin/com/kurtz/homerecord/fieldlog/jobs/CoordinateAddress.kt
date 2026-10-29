package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.core.geo.GeoPoint

/**
 * A job address typed as coordinates ("40.0379, -75.3433", as a map app copies them) is a location already: a lot
 * with no street number, a rural site. It needs no geocoder and works offline (AC-F5, AC-F9).
 */
object CoordinateAddress {
    private val PAIR = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")

    fun parse(address: String?): GeoPoint? {
        val m = PAIR.matchEntire(address ?: return null) ?: return null
        val lat = m.groupValues[1].toDouble()
        val lng = m.groupValues[2].toDouble()
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return GeoPoint(lat, lng)
    }
}

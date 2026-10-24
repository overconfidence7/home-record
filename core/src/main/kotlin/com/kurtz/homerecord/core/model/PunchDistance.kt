package com.kurtz.homerecord.core.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Reasons offered when a punch is far from the job (AC-F9). "Other" carries free text. */
enum class FarReason { PickingUpMaterials, SupplyHouse, Shop, Other }

/** A punch's place, from a GPS fix. */
data class GeoFix(val lat: Double, val lng: Double, val accuracyM: Float? = null)

/**
 * Decides whether a punch is far from its job (AC-F9). No fix or no job location never flags:
 * that punch is "no location", which is a different note.
 */
object PunchDistance {
    private const val EARTH_RADIUS_FT = 20_902_231.0

    fun feetBetween(a: GeoFix, lat: Double, lng: Double): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(lat)
        val dLat = p2 - p1
        val dLng = Math.toRadians(lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(p1) * cos(p2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_FT * asin(sqrt(h))
    }

    fun isFarFromJob(fix: GeoFix?, jobLat: Double?, jobLng: Double?, thresholdFt: Int): Boolean {
        if (fix == null || jobLat == null || jobLng == null) return false
        return feetBetween(fix, jobLat, jobLng) > thresholdFt
    }

    /** The reason text stored with the punch: the picker label, or the typed text for Other. Null when still missing. */
    fun reasonText(reason: FarReason?, other: String): String? = when (reason) {
        null -> null
        FarReason.PickingUpMaterials -> "Picking up materials"
        FarReason.SupplyHouse -> "Supply house"
        FarReason.Shop -> "Shop"
        FarReason.Other -> other.trim().takeIf { it.isNotEmpty() }
    }
}

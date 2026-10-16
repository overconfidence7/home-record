package com.kurtz.homerecord.core.logic

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the equipment list shows beside an item (AC-44). */
sealed interface WarrantyStatus {
    data class Until(val date: String) : WarrantyStatus
    data object Expired : WarrantyStatus
}

/** Pure age and warranty math for the equipment list. */
object AgeAndWarrantyFormatter {
    /** Whole years (floor) between [installedOn] and [today]; null when unset, unparseable or in the future. */
    fun ageYears(installedOn: String?, today: LocalDate): Int? {
        val installed = installedOn?.let { EquipmentRule.parseDate(it) } ?: return null
        if (installed.isAfter(today)) return null
        return ChronoUnit.YEARS.between(installed, today).toInt()
    }

    /** "Warranty until <date>" while the end date is today or later, otherwise expired. */
    fun warranty(warrantyEnds: String?, today: LocalDate): WarrantyStatus? {
        val ends = warrantyEnds?.let { EquipmentRule.parseDate(it) } ?: return null
        return if (ends.isBefore(today)) WarrantyStatus.Expired else WarrantyStatus.Until(ends.toString())
    }
}

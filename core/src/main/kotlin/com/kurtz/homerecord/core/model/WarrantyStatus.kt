package com.kurtz.homerecord.core.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Where a warranty stands today (AC-C33). */
sealed interface WarrantyStatus {
    data object Unknown : WarrantyStatus
    data class Active(val endsOn: String) : WarrantyStatus
    data class Expiring(val endsOn: String, val daysLeft: Int) : WarrantyStatus
    data class Expired(val endsOn: String) : WarrantyStatus

    companion object {
        const val EXPIRING_DAYS = 60

        /** [endsOn] and [today] are ISO `yyyy-MM-dd`; a missing or unreadable end date is [Unknown]. */
        fun of(endsOn: String?, today: String): WarrantyStatus {
            val end = parse(endsOn) ?: return Unknown
            val now = parse(today) ?: return Unknown
            val iso = end.toString()
            val daysLeft = (end.toEpochDay() - now.toEpochDay()).toInt()
            return when {
                daysLeft < 0 -> Expired(iso)
                daysLeft <= EXPIRING_DAYS -> Expiring(iso, daysLeft)
                else -> Active(iso)
            }
        }

        /**
         * Ends-on computed from the in-service date plus the longest of the parts and labor terms
         * (months). Returns null when there is no in-service date or no term.
         */
        fun endsOn(inServiceOn: String?, partsMonths: Int?, laborMonths: Int?): String? {
            val start = parse(inServiceOn) ?: return null
            val months = maxOf(partsMonths ?: 0, laborMonths ?: 0)
            if (months <= 0) return null
            return start.plusMonths(months.toLong()).toString()
        }

        private fun parse(date: String?): LocalDate? =
            if (date.isNullOrBlank()) null else try { LocalDate.parse(date) } catch (_: DateTimeParseException) { null }
    }
}

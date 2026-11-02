package com.kurtz.homerecord.fieldlog.company

import androidx.annotation.StringRes
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.fieldlog.R

/** A zone the FL-Company picker offers, with its plain name. */
data class TimeZoneChoice(val id: String, @StringRes val label: Int?)

/**
 * The time zones FL-Company offers for `settings.timeZone` (AC-F42): the US zones a contractor works in, Eastern first
 * because it is the backend's default. A zone set some other way (the API takes any IANA name) is kept and listed
 * last under its IANA name, so opening the picker never loses it.
 */
object CompanyTimeZones {
    val us: List<TimeZoneChoice> = listOf(
        TimeZoneChoice(CompanySettings.DEFAULT_TIME_ZONE, R.string.company_tz_eastern),
        TimeZoneChoice("America/Chicago", R.string.company_tz_central),
        TimeZoneChoice("America/Denver", R.string.company_tz_mountain),
        TimeZoneChoice("America/Phoenix", R.string.company_tz_arizona),
        TimeZoneChoice("America/Los_Angeles", R.string.company_tz_pacific),
        TimeZoneChoice("America/Anchorage", R.string.company_tz_alaska),
        TimeZoneChoice("Pacific/Honolulu", R.string.company_tz_hawaii),
    )

    /** The zone in effect: the company's, or the backend default when none is set. */
    fun effective(zone: String?): String = zone?.takeIf { it.isNotBlank() } ?: CompanySettings.DEFAULT_TIME_ZONE

    /** The picker's rows for a company whose zone is [current]. */
    fun choices(current: String?): List<TimeZoneChoice> {
        val zone = effective(current)
        return if (us.any { it.id == zone }) us else us + TimeZoneChoice(zone, null)
    }

    fun choiceFor(zone: String?): TimeZoneChoice = effective(zone).let { id -> us.firstOrNull { it.id == id } ?: TimeZoneChoice(id, null) }
}

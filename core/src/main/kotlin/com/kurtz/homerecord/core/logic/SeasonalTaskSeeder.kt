package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Season
import java.time.LocalDate

enum class ClimateZone(val label: String) {
    COLD("Cold"), MIXED("Mixed"), HOT_HUMID("Hot-humid"), HOT_DRY("Hot-dry");

    companion object {
        fun fromName(name: String?): ClimateZone? = entries.firstOrNull { it.name == name }
    }
}

data class SeasonalTaskTemplate(val title: String, val season: Season, val nextDue: String)

/** AC-80: the seasonal checklist a climate zone starts with. Titles are stored as the user's own editable data. */
object SeasonalTaskSeeder {
    private val common = listOf(
        "Test smoke and CO alarms" to Season.SPRING,
        "Test smoke and CO alarms" to Season.FALL,
        "Replace HVAC filter" to Season.SUMMER,
        "Replace HVAC filter" to Season.WINTER,
        "Flush water heater" to Season.FALL,
    )

    private val byZone: Map<ClimateZone, List<Pair<String, Season>>> = mapOf(
        ClimateZone.COLD to listOf(
            "Clean gutters" to Season.SPRING,
            "Inspect roof and chimney" to Season.SPRING,
            "Service furnace" to Season.FALL,
            "Drain outdoor faucets and shut off hose bibs" to Season.FALL,
            "Check attic insulation and ice-dam risk" to Season.WINTER,
        ),
        ClimateZone.MIXED to listOf(
            "Clean gutters" to Season.SPRING,
            "Service A/C" to Season.SPRING,
            "Service furnace" to Season.FALL,
            "Clean gutters" to Season.FALL,
            "Drain outdoor faucets before first freeze" to Season.FALL,
        ),
        ClimateZone.HOT_HUMID to listOf(
            "Service A/C and clear condensate drain" to Season.SPRING,
            "Check for mold and moisture" to Season.SUMMER,
            "Inspect roof and prepare for storm season" to Season.SUMMER,
            "Clean gutters" to Season.FALL,
        ),
        ClimateZone.HOT_DRY to listOf(
            "Service A/C or swamp cooler" to Season.SPRING,
            "Replace swamp-cooler pads" to Season.SPRING,
            "Check sprinkler and irrigation lines" to Season.SUMMER,
            "Seal gaps for pests and dust" to Season.FALL,
        ),
    )

    fun templates(zone: ClimateZone, today: LocalDate): List<SeasonalTaskTemplate> =
        (byZone.getValue(zone) + common)
            .map { (title, season) ->
                SeasonalTaskTemplate(title, season, DueDateMath.nextSeasonStart(today, season).toString())
            }
            .sortedWith(compareBy({ it.nextDue }, { it.title }))
}

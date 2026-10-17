package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel

/** One printed legend page. [panelName] is null when the home has a single panel, so the title stays plain. */
data class LegendPage(val panelName: String?, val subtitle: String?, val rows: List<LegendRow>)

/** AC-98: the legend prints one page per panel, oldest (main) panel first. */
object LegendPagePlanner {
    fun plan(
        panels: List<Panel>,
        breakers: List<Breaker>,
        roomNamesFor: (Breaker) -> List<String>,
        typeLabel: (Breaker) -> String,
        feedsNote: (Breaker) -> String? = { null },
        subtitleFor: (Panel) -> String? = { null },
    ): List<LegendPage> {
        val ordered = panels.sortedBy { it.createdAt }
        return ordered.map { panel ->
            LegendPage(
                panelName = panel.name.takeIf { ordered.size > 1 },
                subtitle = subtitleFor(panel),
                rows = LegendRowBuilder.build(
                    panel.slotCount, breakers.filter { it.panelId == panel.id }, roomNamesFor, typeLabel, feedsNote,
                ),
            )
        }
    }
}

/** AC-97: default name for an added panel, e.g. "Sub-panel 2", unique among [existing]. */
object PanelNamer {
    fun next(base: String, existing: List<String>): String {
        val taken = existing.map { it.trim().lowercase() }.toSet()
        var n = existing.size + 1
        while ("$base $n".lowercase() in taken) n++
        return "$base $n"
    }
}

package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelKind

/** Localized fragments for the legend header line, supplied from strings.xml. */
data class LegendHeaderText(
    val mainPanel: String,
    val subPanel: String,
    val ratingMain: (Int) -> String,
    val ratingSub: (Int) -> String,
    val installed: (Int) -> String,
)

/** AC-105: the legend header line: kind, brand, model, rating ("200 A main"), year installed; unknown parts are left out. */
object LegendHeaderBuilder {
    fun build(panel: Panel, text: LegendHeaderText): String {
        val isMain = panel.kind == PanelKind.MAIN
        return listOfNotNull(
            if (isMain) text.mainPanel else text.subPanel,
            panel.brand?.takeIf { it.isNotBlank() },
            panel.model?.takeIf { it.isNotBlank() },
            panel.ratingAmps?.let { if (isMain) text.ratingMain(it) else text.ratingSub(it) },
            panel.yearInstalled?.let(text.installed),
        ).joinToString(" · ")
    }
}

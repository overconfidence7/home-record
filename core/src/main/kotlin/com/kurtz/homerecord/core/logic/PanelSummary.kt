package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker

/** HM-Home Panel card numbers: slot count and the share of slots covered by a labeled breaker. */
object PanelSummary {
    fun percentLabeled(slotCount: Int, breakers: List<Breaker>): Int {
        if (slotCount <= 0) return 0
        val labeled = breakers.filter { it.label.isNotBlank() }.flatMap { it.slots }.toSet().size
        return (labeled * 100.0 / slotCount).toInt().coerceIn(0, 100)
    }
}

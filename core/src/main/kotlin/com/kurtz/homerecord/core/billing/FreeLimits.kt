package com.kurtz.homerecord.core.billing

enum class LimitedAction { ADD_PANEL, ADD_EQUIPMENT }

enum class LimitResult { ALLOWED, SHOW_PAYWALL }

/** Free tier rules (AC-92, AC-93). Pure; counts are the records that exist before the action. */
object FreeLimits {
    const val MAX_PANELS = 1
    const val MAX_EQUIPMENT = 10

    fun check(
        action: LimitedAction,
        isPro: Boolean,
        panelCount: Int,
        equipmentCount: Int,
        labeledBreakerCount: Int,
    ): LimitResult {
        if (isPro) return LimitResult.ALLOWED
        // AC-93: the paywall never appears before the panel has real, labeled content.
        if (labeledBreakerCount < 1) return LimitResult.ALLOWED
        val overLimit = when (action) {
            LimitedAction.ADD_PANEL -> panelCount >= MAX_PANELS
            LimitedAction.ADD_EQUIPMENT -> equipmentCount >= MAX_EQUIPMENT
        }
        return if (overLimit) LimitResult.SHOW_PAYWALL else LimitResult.ALLOWED
    }
}

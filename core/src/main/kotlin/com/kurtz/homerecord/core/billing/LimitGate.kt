package com.kurtz.homerecord.core.billing

import com.kurtz.homerecord.core.repo.EquipmentRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import kotlinx.coroutines.flow.first

/** Reads the current counts and entitlement and applies [FreeLimits] before a save. */
class LimitGate(
    private val entitlements: Entitlements,
    private val panels: PanelRepository,
    private val equipment: EquipmentRepository,
) {
    suspend fun check(homeId: String, action: LimitedAction): LimitResult {
        if (entitlements.isPro.value) return LimitResult.ALLOWED
        return FreeLimits.check(
            action = action,
            isPro = false,
            panelCount = panels.observePanels(homeId).first().size,
            equipmentCount = equipment.observeEquipment(homeId).first().size,
            labeledBreakerCount = panels.observeBreakersForHome(homeId).first().count { it.label.isNotBlank() },
        )
    }
}

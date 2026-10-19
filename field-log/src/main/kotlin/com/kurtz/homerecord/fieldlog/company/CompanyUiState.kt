package com.kurtz.homerecord.fieldlog.company

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.CompanyProfile
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.company.OvertimeRule

/** FL-Company form. Text fields are kept as typed; [punchDistanceText] is parsed on save. */
data class CompanyUiState(
    val status: TeamStatus = TeamStatus.Loading,
    val failure: ApiFailure? = null,
    val loaded: CompanyProfile? = null,
    val name: String = "",
    val trade: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val overtimeRule: OvertimeRule = OvertimeRule.None,
    val breakRule: Boolean = false,
    val requireGps: Boolean = false,
    val punchDistanceText: String = CompanySettings.DEFAULT_PUNCH_DISTANCE_FT.toString(),
    val photoOnPunch: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val saveFailure: ApiFailure? = null,
) {
    val punchDistanceFt: Int? get() = punchDistanceText.trim().toIntOrNull()?.takeIf {
        it in CompanySettings.MIN_PUNCH_DISTANCE_FT..CompanySettings.MAX_PUNCH_DISTANCE_FT
    }
    val distanceInvalid: Boolean get() = punchDistanceFt == null
    val canSave: Boolean get() = status == TeamStatus.Ready && !saving && name.isNotBlank() && !distanceInvalid && dirty

    val dirty: Boolean
        get() {
            val p = loaded ?: return false
            return name != p.name || trade != p.trade.orEmpty() || phone != p.phone.orEmpty() || email != p.email.orEmpty() ||
                address != p.address.orEmpty() || overtimeRule != p.settings.overtimeRule || breakRule != p.settings.breakRule ||
                requireGps != p.settings.requireGps || punchDistanceText.trim() != p.settings.punchDistanceFt.toString() ||
                photoOnPunch != p.settings.photoOnPunch
        }
}

sealed interface CompanyEvent {
    data class Name(val value: String) : CompanyEvent
    data class Trade(val value: String) : CompanyEvent
    data class Phone(val value: String) : CompanyEvent
    data class Email(val value: String) : CompanyEvent
    data class Address(val value: String) : CompanyEvent
    data class Overtime(val rule: OvertimeRule) : CompanyEvent
    data class BreakRule(val on: Boolean) : CompanyEvent
    data class RequireGps(val on: Boolean) : CompanyEvent
    data class PunchDistance(val value: String) : CompanyEvent
    data class PhotoOnPunch(val on: Boolean) : CompanyEvent
    data object Save : CompanyEvent
    data object Retry : CompanyEvent
}

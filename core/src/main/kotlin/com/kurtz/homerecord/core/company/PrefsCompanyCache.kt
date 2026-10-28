package com.kurtz.homerecord.core.company

import android.content.Context

class PrefsCompanyCache(context: Context) : CompanyCache {
    private val prefs = context.getSharedPreferences("company_cache", Context.MODE_PRIVATE)

    override fun read(): CompanyMembership? {
        val id = prefs.getString(ID, null) ?: return null
        return CompanyMembership(
            companyId = id,
            companyName = prefs.getString(NAME, "").orEmpty(),
            role = CompanyRole.fromWire(prefs.getString(ROLE, "").orEmpty()),
            trialEndsAt = prefs.getLong(TRIAL, -1L).takeIf { it >= 0 },
            entitlement = prefs.getString(ENTITLEMENT, null),
            settings = CompanySettings(
                overtimeRule = OvertimeRule.fromWire(prefs.getString(OT_RULE, "").orEmpty()),
                breakRule = prefs.getBoolean(BREAK_RULE, false),
                requireGps = prefs.getBoolean(REQUIRE_GPS, false),
                punchDistanceFt = prefs.getInt(PUNCH_DISTANCE, CompanySettings.DEFAULT_PUNCH_DISTANCE_FT),
                photoOnPunch = prefs.getBoolean(PHOTO_ON_PUNCH, false),
            ),
        )
    }

    override fun write(membership: CompanyMembership?) {
        val editor = prefs.edit().clear()
        membership?.let {
            editor.putString(ID, it.companyId)
            editor.putString(NAME, it.companyName)
            editor.putString(ROLE, it.role.wire)
            it.trialEndsAt?.let { t -> editor.putLong(TRIAL, t) }
            it.entitlement?.let { e -> editor.putString(ENTITLEMENT, e) }
            editor.putString(OT_RULE, it.settings.overtimeRule.wire)
            editor.putBoolean(BREAK_RULE, it.settings.breakRule)
            editor.putBoolean(REQUIRE_GPS, it.settings.requireGps)
            editor.putInt(PUNCH_DISTANCE, it.settings.punchDistanceFt)
            editor.putBoolean(PHOTO_ON_PUNCH, it.settings.photoOnPunch)
        }
        editor.apply()
    }

    private companion object {
        const val ID = "id"
        const val NAME = "name"
        const val ROLE = "role"
        const val TRIAL = "trial"
        const val ENTITLEMENT = "entitlement"
        const val OT_RULE = "ot_rule"
        const val BREAK_RULE = "break_rule"
        const val REQUIRE_GPS = "require_gps"
        const val PUNCH_DISTANCE = "punch_distance_ft"
        const val PHOTO_ON_PUNCH = "photo_on_punch"
    }
}

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
        }
        editor.apply()
    }

    private companion object {
        const val ID = "id"
        const val NAME = "name"
        const val ROLE = "role"
        const val TRIAL = "trial"
        const val ENTITLEMENT = "entitlement"
    }
}

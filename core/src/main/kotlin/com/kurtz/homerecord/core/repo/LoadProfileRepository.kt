package com.kurtz.homerecord.core.repo

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.logic.HvacKind
import com.kurtz.homerecord.core.logic.LoadAppliance
import com.kurtz.homerecord.core.model.LoadProfile
import com.kurtz.homerecord.core.model.Source

/** AC-114: the saved load-estimate inputs, one row per home. */
class LoadProfileRepository(
    private val db: HomeRecordDatabase,
    private val source: Source,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    suspend fun get(homeId: String): LoadProfile? = db.loadProfileDao().getForHome(homeId)?.toDomain()

    suspend fun save(
        homeId: String,
        appliances: Map<LoadAppliance, Int>,
        hvac: HvacKind,
        hvacVa: Int,
        stripVa: Int,
    ): LoadProfile {
        val now = clock.now()
        val existing = get(homeId)
        val profile = LoadProfile(
            existing?.id ?: ids.next(), homeId, appliances, hvac, hvacVa, stripVa,
            existing?.createdAt ?: now, now, existing?.source ?: source,
        )
        db.loadProfileDao().upsert(profile.toEntity())
        return profile
    }
}

package com.kurtz.homerecord.housemodel.reminders

import androidx.annotation.StringRes
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.MaintenanceTaskEntity
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.housemodel.R
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One reminder as HM-Reminders lists it. [itemName] is set when it belongs to a live item. */
data class ReminderRow(
    val id: String,
    val title: String,
    val itemId: String?,
    val itemName: String?,
    val bucket: ReminderBucket,
    val dueOn: LocalDate,
    val intervalMonths: Int?,
    val lastDoneOn: LocalDate?,
    val snoozedUntil: LocalDate?,
    val seasonal: Boolean,
)

/** What a new hand-written reminder needs. */
data class ReminderDraft(val title: String = "", val dueOn: LocalDate? = null, val intervalMonths: Int? = null, val itemId: String? = null) {
    val valid: Boolean get() = title.isNotBlank() && dueOn != null && (intervalMonths == null || intervalMonths in 1..120)
}

/**
 * Reminders of a home (AC-C49, AC-H12): the list in sections, Done, Snooze, hand-written reminders, the seasonal set a
 * climate zone seeds and the warranty-expiry pair. Item defaults are seeded by [com.kurtz.homerecord.housemodel.items.ItemRepository].
 * [onChange] is called with the entity name and id after every write so the sync outbox can pick it up.
 */
class ReminderRepository(
    private val db: HomeRecordDatabase,
    private val strings: (Int) -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
    private val source: String = Sources.HOUSE_MANUAL,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    private val dao get() = db.maintenanceTaskDao()

    /** Every listed reminder of the home in its section, Due now and Upcoming by date, Done newest first. */
    fun observe(homeId: String): Flow<List<ReminderRow>> =
        combine(dao.observeFor(homeId), db.itemDao().observeFor(homeId)) { tasks, items ->
            val names = items.associate { it.id to it.name }
            val day = today()
            tasks.mapNotNull { t -> t.toRow(names, day) }
                .sortedWith(compareBy<ReminderRow> { it.bucket.ordinal }.thenBy { row ->
                    if (row.bucket == ReminderBucket.DONE) -(row.lastDoneOn?.toEpochDay() ?: 0) else effectiveDue(row).toEpochDay()
                }.thenBy { it.title.lowercase() })
        }

    /** Live reminders of the home that are due now: what the daily notification counts (AC-C50). */
    suspend fun dueNow(homeId: String): List<MaintenanceTaskEntity> {
        val day = today()
        return dao.listFor(homeId).filter { t -> t.dates()?.let { MaintenanceRule.dueToday(it, day) } == true }
    }

    /** Count of live reminders of the home, for asking for notification permission after the first one exists (AC-C50). */
    suspend fun count(homeId: String): Int = dao.listFor(homeId).size

    suspend fun done(id: String) = update(id) { MaintenanceRule.done(it, today()) }

    suspend fun snooze(id: String, length: SnoozeLength) = update(id) { MaintenanceRule.snooze(it, today(), length) }

    suspend fun delete(id: String) {
        dao.softDelete(id, now())
        onChange("maintenance_task", id)
    }

    /** Saves a hand-written reminder; null when the draft is not valid. */
    suspend fun add(homeId: String, draft: ReminderDraft): String? {
        val due = draft.dueOn ?: return null
        if (!draft.valid) return null
        val id = Ids.newId()
        val at = now()
        dao.upsert(
            MaintenanceTaskEntity(
                id = id, homeId = homeId, itemId = draft.itemId, title = draft.title.trim(), intervalMonths = draft.intervalMonths,
                dueOn = due.toString(), createdAt = at, updatedAt = at, source = source,
            ),
        )
        onChange("maintenance_task", id)
        return id
    }

    /**
     * Picks the home's climate zone and seeds its seasonal tasks, each due on the next 1st of its month. A zone picked
     * again keeps its tasks; a different zone replaces the old seasonal set (hand-written and item reminders stay).
     */
    suspend fun setClimateZone(homeId: String, zone: ClimateZone) {
        val home = db.homeDao().get(homeId) ?: return
        val at = now()
        val existing = dao.listFor(homeId).filter { it.seasonal }
        if (home.climateZone == zone.id && existing.isNotEmpty()) return
        db.homeDao().upsert(home.copy(climateZone = zone.id, updatedAt = at))
        onChange("home", homeId)
        existing.forEach {
            dao.softDelete(it.id, at)
            onChange("maintenance_task", it.id)
        }
        val day = today()
        MaintenanceRule.seasonalTasks(zone).forEach { task ->
            val id = Ids.newId()
            dao.upsert(
                MaintenanceTaskEntity(
                    id = id, homeId = homeId, title = strings(seasonalTitleRes(task.key)), intervalMonths = 12,
                    dueOn = MaintenanceRule.nextSeasonalDue(task.month, day).toString(), seasonal = true,
                    createdAt = at, updatedAt = at, source = source,
                ),
            )
            onChange("maintenance_task", id)
        }
    }

    /**
     * Keeps an item's warranty-expiry reminders (60 and 7 days before [endsOn]) in step with its warranty: rows with
     * stable ids per item and lead time, rewritten when the end date moves and removed when it goes away or is past.
     */
    suspend fun syncWarrantyReminders(homeId: String, itemId: String, itemName: String, endsOn: LocalDate?) {
        val at = now()
        val wanted = endsOn?.let { end ->
            MaintenanceRule.WARRANTY_LEAD_DAYS.zip(MaintenanceRule.WARRANTY_LEAD_DAYS.map { end.minusDays(it.toLong()) })
                .filter { (_, date) -> !date.isBefore(today()) }
                .toMap()
        }.orEmpty()
        MaintenanceRule.WARRANTY_LEAD_DAYS.forEach { lead ->
            val id = warrantyReminderId(itemId, lead)
            val row = dao.get(id)
            val date = wanted[lead]
            when {
                date == null && row != null -> { dao.softDelete(id, at); onChange("maintenance_task", id) }
                date != null && (row == null || row.dueOn != date.toString()) -> {
                    dao.upsert(
                        MaintenanceTaskEntity(
                            id = id, homeId = homeId, itemId = itemId, title = strings(R.string.reminder_warranty_ends).format(itemName),
                            dueOn = date.toString(), createdAt = row?.createdAt ?: at, updatedAt = at, source = source,
                        ),
                    )
                    onChange("maintenance_task", id)
                }
            }
        }
    }

    private suspend fun update(id: String, change: (ReminderDates) -> ReminderDates) {
        val row = dao.get(id) ?: return
        val dates = row.dates() ?: return
        val next = change(dates)
        dao.upsert(
            row.copy(
                dueOn = next.dueOn.toString(), lastDoneOn = next.lastDoneOn?.toString(), snoozedUntil = next.snoozedUntil?.toString(),
                updatedAt = now(),
            ),
        )
        onChange("maintenance_task", id)
    }

    private fun effectiveDue(row: ReminderRow) =
        MaintenanceRule.effectiveDue(ReminderDates(row.dueOn, row.intervalMonths, row.lastDoneOn, row.snoozedUntil))

    private fun MaintenanceTaskEntity.toRow(names: Map<String, String>, day: LocalDate): ReminderRow? {
        val d = dates() ?: return null
        val bucket = MaintenanceRule.bucket(d, day) ?: return null
        return ReminderRow(
            id = id, title = title, itemId = itemId?.takeIf { it in names }, itemName = itemId?.let { names[it] }, bucket = bucket,
            dueOn = d.dueOn, intervalMonths = intervalMonths, lastDoneOn = d.lastDoneOn, snoozedUntil = d.snoozedUntil, seasonal = seasonal,
        )
    }

    companion object {
        /** The stable id of an item's warranty reminder [leadDays] before the end, so a moved end date rewrites it. */
        fun warrantyReminderId(itemId: String, leadDays: Int): String =
            UUID.nameUUIDFromBytes("warranty-reminder:$itemId:$leadDays".toByteArray()).toString()
    }
}

/** The stored dates, or null when a date does not parse (a bad row is left out rather than crashing the list). */
internal fun MaintenanceTaskEntity.dates(): ReminderDates? = runCatching {
    ReminderDates(
        dueOn = LocalDate.parse(dueOn),
        intervalMonths = intervalMonths,
        lastDoneOn = lastDoneOn?.let(LocalDate::parse),
        snoozedUntil = snoozedUntil?.let(LocalDate::parse),
    )
}.getOrNull()

/** The text of a seasonal task. */
@StringRes
fun seasonalTitleRes(key: String): Int = seasonalTitles[key] ?: R.string.seasonal_check_heating

/** The climate zone's chip label. */
@StringRes
fun ClimateZone.labelRes(): Int = when (this) {
    ClimateZone.COLD -> R.string.climate_zone_cold
    ClimateZone.MIXED -> R.string.climate_zone_mixed
    ClimateZone.HOT_HUMID -> R.string.climate_zone_hot_humid
    ClimateZone.HOT_DRY -> R.string.climate_zone_hot_dry
    ClimateZone.MARINE -> R.string.climate_zone_marine
}

internal val seasonalTitles: Map<String, Int> = mapOf(
    "seasonal_test_ac" to R.string.seasonal_test_ac,
    "seasonal_check_heating" to R.string.seasonal_check_heating,
    "seasonal_drain_outdoor_faucets" to R.string.seasonal_drain_outdoor_faucets,
    "seasonal_check_weatherstripping" to R.string.seasonal_check_weatherstripping,
    "seasonal_check_ice_dams" to R.string.seasonal_check_ice_dams,
    "seasonal_storm_prep" to R.string.seasonal_storm_prep,
    "seasonal_check_moisture" to R.string.seasonal_check_moisture,
    "seasonal_clear_ac_drain" to R.string.seasonal_clear_ac_drain,
    "seasonal_service_evap_cooler" to R.string.seasonal_service_evap_cooler,
    "seasonal_clear_brush" to R.string.seasonal_clear_brush,
    "seasonal_check_roof_sun" to R.string.seasonal_check_roof_sun,
    "seasonal_clear_moss" to R.string.seasonal_clear_moss,
)

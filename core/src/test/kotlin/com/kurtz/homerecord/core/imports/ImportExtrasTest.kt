package com.kurtz.homerecord.core.imports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.bundle.BundleReader
import com.kurtz.homerecord.core.bundle.BundleWriter
import com.kurtz.homerecord.core.bundle.RecordBundle
import com.kurtz.homerecord.core.bundle.RecordJson
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.logic.HvacKind
import com.kurtz.homerecord.core.logic.LoadAppliance
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Consumable
import com.kurtz.homerecord.core.model.ConsumableKind
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.LoadProfile
import com.kurtz.homerecord.core.model.MaintenanceRule
import com.kurtz.homerecord.core.model.MaintenanceTask
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HandoffRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ImportExtrasTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun home(id: String) = Home(id, "Elm", createdAt = 1, updatedAt = 1, source = Source.HOUSE_MANUAL)
    private fun furnace(homeId: String) =
        Equipment("e1", homeId, category = EquipmentCategory.FURNACE, createdAt = 1, updatedAt = 1, source = Source.HOUSE_MANUAL)
    private fun filter() = Consumable("c1", "e1", ConsumableKind.FILTER, "Filter 16x25x1", null, 1, 1, Source.HOUSE_MANUAL)
    private fun task(homeId: String) = MaintenanceTask(
        "t1", homeId, "e1", "Change filter", MaintenanceRule.CALENDAR, intervalMonths = 3,
        nextDue = "2026-12-01", createdAt = 1, updatedAt = 1, source = Source.HOUSE_MANUAL,
    )
    private fun load(id: String, homeId: String, stripVa: Int = 500) = LoadProfile(
        id, homeId, mapOf(LoadAppliance.RANGE to 12_000), HvacKind.HEAT_PUMP, 4_000, stripVa, 1, 1, Source.HOUSE_MANUAL,
    )

    private suspend fun seeded(): HomeRecordDatabase {
        val db = HomeRecordDatabase.inMemory(context)
        db.homeDao().upsert(home("h1").toEntity())
        db.equipmentDao().upsert(furnace("h1").toEntity())
        db.consumableDao().upsertAll(listOf(filter().toEntity()))
        db.maintenanceTaskDao().upsert(task("h1").toEntity())
        db.loadProfileDao().upsert(load("lp1", "h1").toEntity())
        return db
    }

    @Test fun `bundle carries consumables, reminders and load profile through zip and onto a fresh install (AC-72, AC-90)`() = runTest {
        val db1 = seeded()
        val bundle = HandoffRepository(db1).bundleFor("h1")!!
        db1.close()
        assertEquals(listOf("c1"), bundle.consumables.map { it.id })
        assertEquals(listOf("t1"), bundle.maintenanceTasks.map { it.id })
        assertEquals(listOf("lp1"), bundle.loadProfiles.map { it.id })

        val zip = ByteArrayOutputStream().also { BundleWriter(MediaFileStore(tmp.newFolder("f1"))).write(bundle, it) }
        val read = BundleReader().read(zip.toByteArray().inputStream(), tmp.newFolder("staging")).record

        val db2 = HomeRecordDatabase.inMemory(context)
        val applier = ImportApplier(db2, MediaFileStore(tmp.newFolder("f2")))
        val plan = applier.plan(read)
        assertEquals(1, plan.newCounts[ImportEntityType.CONSUMABLE])
        assertEquals(1, plan.newCounts[ImportEntityType.MAINTENANCE_TASK])
        assertEquals(1, plan.newCounts[ImportEntityType.LOAD_PROFILE])
        applier.apply(plan, emptyMap(), tmp.newFolder("staging2"))
        assertEquals("Filter 16x25x1", db2.consumableDao().getForEquipment("e1").single().spec)
        assertEquals("Change filter", db2.maintenanceTaskDao().get("t1")?.title)
        assertEquals(Source.IMPORT, db2.maintenanceTaskDao().get("t1")?.source)
        assertEquals("lp1", db2.loadProfileDao().getForHome("h1")?.id)

        // AC-79: running the same import again adds nothing.
        assertEquals(0, applier.plan(read).newTotal)
        db2.close()
    }

    @Test fun `bundles written before the optional arrays still decode`() {
        val full = Json.parseToJsonElement(RecordJson.encode(RecordBundle(home = home("h")))).jsonObject
        val old = JsonObject(full - setOf("consumables", "maintenanceTasks", "loadProfiles")).toString()
        val decoded = RecordJson.decode(old)
        assertTrue(decoded.consumables.isEmpty() && decoded.maintenanceTasks.isEmpty() && decoded.loadProfiles.isEmpty())
    }

    @Test fun `an imported load profile never duplicates the local one`() = runTest {
        val db = HomeRecordDatabase.inMemory(context)
        db.homeDao().upsert(home("local").toEntity())
        db.loadProfileDao().upsert(load("mine", "local", stripVa = 500).toEntity())
        val bundle = RecordBundle(home = home("other"), loadProfiles = listOf(load("theirs", "other", stripVa = 900)))
        val applier = ImportApplier(db, MediaFileStore(tmp.newFolder("f")))
        val plan = applier.plan(bundle)
        assertEquals(listOf(ImportEntityType.LOAD_PROFILE), plan.conflicts.map { it.type })
        applier.apply(plan, mapOf("mine" to ConflictChoice.USE_IMPORTED), tmp.newFolder("s"))
        val only = db.loadProfileDao().getAll().single()
        assertEquals("mine", only.id)
        assertEquals(900, only.stripVa)
        assertEquals("local", only.homeId)
        db.close()
    }
}

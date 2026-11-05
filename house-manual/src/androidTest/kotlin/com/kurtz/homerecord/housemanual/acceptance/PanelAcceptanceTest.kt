package com.kurtz.homerecord.housemanual.acceptance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.panel.BreakerDraft
import com.kurtz.homerecord.housemodel.panel.BreakerSave
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The breaker panel on the device's own SQLite (AC-C40): a Breaker panel item gets a 40-slot two-column panel, a
 * two-pole breaker fills two slots as one tall cell, a long-pressed slot takes a second breaker as a tandem, and the
 * rooms and items a breaker feeds are stored with it. The HM-Panel screen steps join this test with T066b/T066c.
 */
@RunWith(AndroidJUnit4::class)
class PanelAcceptanceTest {
    private lateinit var db: HomeRecordDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    @AcceptanceCriterion("AC-C40")
    @Test fun panelSlotsTwoPoleTandemAndFeeds() = runBlocking<Unit> {
        db.itemDao().upsert(ItemEntity(id = "p", homeId = "h", category = "ELECTRICAL", subtype = "breaker_panel", name = "Main panel", createdAt = 1, updatedAt = 1))
        val repo = PanelRepository(db)
        val panel = repo.ensurePanel("p")
        val range = (repo.saveBreaker(panel, BreakerDraft(startSlot = 1, poles = 2, amps = "40", label = "Range", roomIds = setOf("kitchen"))) as BreakerSave.Saved).id
        repo.saveBreaker(panel, BreakerDraft(startSlot = 14, amps = "15", label = "Hall lights"))
        repo.saveBreaker(panel, BreakerDraft(startSlot = 14, amps = "15", label = "Attic", tandem = true))

        val state = checkNotNull(repo.observe("p").first())
        assertThat(state.panel.slotCount).isEqualTo(40)
        assertThat(state.grid.cells.first { it.slot == 1 }.rowSpan).isEqualTo(2)
        assertThat(state.grid.cells.first { it.slot == 14 }.isTandem).isTrue()
        assertThat(state.links.getValue(range).single().roomId).isEqualTo("kitchen")
    }
}

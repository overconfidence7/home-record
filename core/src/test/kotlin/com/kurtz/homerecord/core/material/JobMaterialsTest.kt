package com.kurtz.homerecord.core.material

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F44 (a receipt logged to a job with vendor, amount, category, date and note; each write syncs)
@AcceptanceCriterion("AC-F44")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobMaterialsTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var materials: JobMaterials
    private val changes = mutableListOf<String>()
    private fun draft(job: String? = "j1", cents: Long? = 12_371) =
        MaterialDraft(job, "  City Electric ", cents, MaterialCategory.MATERIALS, "2026-11-04", " ")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        materials = JobMaterials(db, now = { 7_000L }, onChange = { e, id -> changes += "$e/$id" })
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1))
            db.jobDao().upsert(JobEntity(id = "j2", companyId = "c2", name = "Other company", createdAt = 1, updatedAt = 1))
        }
    }

    @After fun tearDown() = db.close()

    @Test fun saveTrimsAndQueuesOneChange() = runBlocking<Unit> {
        val id = (materials.save(draft()) as MaterialSaveResult.Saved).id
        val saved = checkNotNull(materials.get(id))
        assertThat(saved.vendor).isEqualTo("City Electric")
        assertThat(saved.note).isNull()
        assertThat(saved.amountCents).isEqualTo(12_371L)
        assertThat(changes).containsExactly("material/$id")
        assertThat(materials.observeForCompany("c1").first().map { it.id }).containsExactly(id)
        assertThat(materials.observeForCompany("c2").first()).isEmpty()
    }

    @Test fun needsAJobAndAnAmount() = runBlocking<Unit> {
        assertThat(materials.save(draft(job = null))).isEqualTo(MaterialSaveResult.NoJob)
        assertThat(materials.save(draft(job = "missing"))).isEqualTo(MaterialSaveResult.NoJob)
        assertThat(materials.save(draft(cents = null))).isEqualTo(MaterialSaveResult.NoAmount)
        assertThat(materials.save(draft(cents = 0))).isEqualTo(MaterialSaveResult.NoAmount)
        assertThat(changes).isEmpty()
    }

    @Test fun editKeepsTheIdAndDeleteHidesIt() = runBlocking<Unit> {
        val id = (materials.save(draft()) as MaterialSaveResult.Saved).id
        assertThat(materials.save(draft(cents = 500).copy(category = MaterialCategory.FUEL), id)).isEqualTo(MaterialSaveResult.Saved(id))
        assertThat(materials.get(id)?.category).isEqualTo(MaterialCategory.FUEL)
        assertThat(materials.delete(id)).isTrue()
        assertThat(materials.delete(id)).isFalse()
        assertThat(materials.save(draft(), id)).isEqualTo(MaterialSaveResult.Missing)
        assertThat(materials.observeForCompany("c1").first()).isEmpty()
        assertThat(changes).hasSize(3)
    }
}

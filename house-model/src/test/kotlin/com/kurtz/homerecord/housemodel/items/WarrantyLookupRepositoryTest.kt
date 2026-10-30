package com.kurtz.homerecord.housemodel.items

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.core.warranty.WarrantyLookupApi
import com.kurtz.homerecord.core.warranty.WarrantyTerms
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeApi(var result: ApiResult<WarrantyTerms>) : WarrantyLookupApi {
    var calls = 0
    override suspend fun lookup(make: String, model: String, category: String?, serial: String?): ApiResult<WarrantyTerms> {
        calls++
        return result
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WarrantyLookupRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var items: ItemRepository
    private val terms = WarrantyTerms(partsMonths = 72, laborMonths = 12, specialTerms = listOf("tank 12 years"), confidence = "HIGH", sourceUrl = "https://rheem.com/w")
    private val api = FakeApi(ApiResult.Ok(terms))
    private lateinit var repo: WarrantyLookupRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        items = ItemRepository(db, ApplicationProvider.getApplicationContext<Context>().resources::getString, now = { 5_000L }, today = { LocalDate.of(2026, 10, 23) })
        repo = WarrantyLookupRepository(db, api, now = { 6_000L })
    }

    @After fun tearDown() = db.close()

    private suspend fun heater(make: String = "Rheem", model: String = "XG50T06", inService: String = "2024-01-15") =
        items.save("h1", ItemDraft(subtypeId = "water_heater", roomId = PseudoRooms.WHOLE_HOUSE, name = "Water heater", make = make, model = model, inServiceOn = inService)).orEmpty()

    @AcceptanceCriterion("AC-C34")
    @Test fun needsMakeAndModel() = runTest {
        val id = heater(model = "")
        assertThat(repo.lookup(id)).isEqualTo(LookupOutcome.NeedsMakeModel)
        assertThat(api.calls).isEqualTo(0)
    }

    @AcceptanceCriterion("AC-C34")
    @Test fun confirmStoresTermsOnceWithEndDate() = runTest {
        val id = heater()
        assertThat(repo.lookup(id)).isEqualTo(LookupOutcome.Found(terms))
        repo.confirm(id, terms)
        val row = db.warrantyDao().listFor(id).single()
        assertThat(row.endsOn).isEqualTo("2030-01-15")
        assertThat(row.confidence).isEqualTo("HIGH")
        assertThat(row.lookedUpAt).isEqualTo(6_000L)
        assertThat(repo.lookup(id)).isEqualTo(LookupOutcome.AlreadyStored)
        assertThat(api.calls).isEqualTo(1)
        assertThat(repo.lookup(id, force = true)).isEqualTo(LookupOutcome.Found(terms))
        assertThat(api.calls).isEqualTo(2)
    }

    @AcceptanceCriterion("AC-C34")
    @Test fun editedTermsAreManualAndDiscardStoresNothing() = runTest {
        val id = heater()
        repo.lookup(id)
        repo.discard(id)
        assertThat(db.warrantyDao().listFor(id)).isEmpty()
        repo.confirm(id, terms.copy(partsMonths = 24), edited = true)
        val row = db.warrantyDao().listFor(id).single()
        assertThat(row.confidence).isEqualTo("MANUAL")
        assertThat(row.partsMonths).isEqualTo(24)
    }

    @AcceptanceCriterion("AC-C34")
    @Test fun offlineQueuesThenRunsWhenOnlineAndShowsReviewBadge() = runTest {
        val id = heater()
        api.result = ApiResult.Err(ApiFailure.Offline)
        assertThat(repo.lookup(id)).isEqualTo(LookupOutcome.Queued)
        assertThat(repo.lookup(id)).isEqualTo(LookupOutcome.Queued)
        assertThat(db.warrantyDao().listFor(id)).hasSize(1)
        assertThat(repo.hasPending(id)).isTrue()
        assertThat(repo.runPending()).isEmpty()

        api.result = ApiResult.Ok(terms)
        assertThat(repo.runPending()).containsExactly(id)
        assertThat(repo.hasPending(id)).isFalse()
        assertThat(repo.reviewTerms(id)).isEqualTo(terms)
        repo.confirm(id, terms)
        assertThat(db.warrantyDao().listFor(id).single().confidence).isEqualTo("HIGH")
        assertThat(repo.reviewTerms(id)).isNull()
    }

    @AcceptanceCriterion("AC-C34")
    @Test fun offlineLookUpAgainDoesNotQueueOverStoredWarranty() = runTest {
        val id = heater()
        repo.confirm(id, terms)
        api.result = ApiResult.Err(ApiFailure.Offline)
        assertThat(repo.lookup(id, force = true)).isEqualTo(LookupOutcome.Failed)
        assertThat(db.warrantyDao().listFor(id).single().confidence).isEqualTo("HIGH")
    }
}

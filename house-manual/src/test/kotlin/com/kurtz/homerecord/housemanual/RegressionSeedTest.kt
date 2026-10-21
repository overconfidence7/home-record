package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.qa.RegressionSeed
import com.kurtz.homerecord.housemodel.HomeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RegressionSeedTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: HomeRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = HomeRepository(db)
    }

    @After fun tearDown() = db.close()

    // AC-C1
    @AcceptanceCriterion("AC-C1")
    @Test fun regressionSeedBuildsThreeFloorsWithTwelveRooms() = runTest {
        val homeId = RegressionSeed.seed(db, repo)
        val floors = repo.observeFloors(homeId).first()
        assertThat(floors).hasSize(3)
        assertThat(floors.sumOf { it.roomCount }).isEqualTo(12)
        val plans = floors.map { repo.loadPlan(it.id) }
        assertThat(plans.sumOf { it.placed.size }).isEqualTo(12)
        assertThat(plans.flatMap { it.placed }.sumOf { it.openings.size }).isGreaterThan(5)
        assertThat(plans.flatMap { it.placed }.sumOf { it.stairs.size }).isEqualTo(1)
    }

    @Test fun seedingTwiceReplacesTheHomeAndResetEmptiesIt() = runTest {
        RegressionSeed.seed(db, repo)
        RegressionSeed.seed(db, repo)
        assertThat(db.homeDao().observeAll().first()).hasSize(1)
        RegressionSeed.reset(db)
        assertThat(repo.observeHome().first()).isNull()
    }
}

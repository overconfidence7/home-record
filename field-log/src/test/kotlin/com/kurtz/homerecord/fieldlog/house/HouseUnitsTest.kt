package com.kurtz.homerecord.fieldlog.house

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.ui.FloorsRoute
import com.kurtz.homerecord.housemodel.ui.LocalUnitSystem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// T065b: the units picked in Field Log's Settings reach the shared house screens (AC-C4).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HouseUnitsTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: HomeRepository
    private lateinit var homeId: String

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = HomeRepository(db, context.resources::getString)
        homeId = runBlocking { repo.createHome(NewHome("Hale kitchen", null, HouseType.RANCHER, Foundation.SLAB, false)) }
    }

    @After fun tearDown() = db.close()

    private fun floors(system: UnitSystem?) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
            if (system == null) {
                FloorsRoute(repo, homeId, "Hale kitchen", onBack = {})
            } else {
                CompositionLocalProvider(LocalUnitSystem provides system) { FloorsRoute(repo, homeId, "Hale kitchen", onBack = {}) }
            }
        }
    }

    private fun shown(text: String): Boolean {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        return true
    }

    // AC-C4
    @Test fun metricChoiceShowsCeilingsInMetres() {
        floors(UnitSystem.METRIC)
        assertThat(shown("2.44 m ceilings")).isTrue()
        assertThat(rule.onAllNodesWithText("8' ceilings", substring = true).fetchSemanticsNodes()).isEmpty()
    }

    // AC-C4
    @Test fun withoutAChoiceLengthsAreFeetAndInches() {
        floors(null)
        assertThat(shown("8' ceilings")).isTrue()
    }
}

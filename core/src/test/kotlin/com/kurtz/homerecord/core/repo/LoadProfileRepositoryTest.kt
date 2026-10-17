package com.kurtz.homerecord.core.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.logic.HvacKind
import com.kurtz.homerecord.core.logic.LoadAppliance
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LoadProfileRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var loads: LoadProfileRepository
    private var tick = 100L
    private val clock = Clock { tick++ }

    @Before fun setUp() {
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        homes = HomeRepository(db, Source.HOUSE_MANUAL, clock)
        loads = LoadProfileRepository(db, Source.HOUSE_MANUAL, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun saveThenGetKeepsOneRowPerHome() = runTest {
        val home = homes.createHome("Home")
        assertNull(loads.get(home.id))
        val first = loads.save(home.id, mapOf(LoadAppliance.RANGE to 12_000), HvacKind.AC_ONLY, 3_500, 0)
        val second = loads.save(home.id, mapOf(LoadAppliance.DRYER to 5_000), HvacKind.HEAT_PUMP_STRIP, 4_000, 10_000)
        val read = loads.get(home.id)!!
        assertEquals(first.id, second.id)
        assertEquals(first.createdAt, read.createdAt)
        assertEquals(mapOf(LoadAppliance.DRYER to 5_000), read.appliances)
        assertEquals(HvacKind.HEAT_PUMP_STRIP, read.hvac)
        assertEquals(10_000, read.stripVa)
    }
}

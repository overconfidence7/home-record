package com.kurtz.homerecord.fieldlog.handoff

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.bundle.BundleReadResult
import com.kurtz.homerecord.core.bundle.BundleReader
import com.kurtz.homerecord.core.bundle.HandoffSummary
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

/** AC-F51: the job's house is written as a `.homerecord` under cache/handoff that reads back with its rooms. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@AcceptanceCriterion("AC-F51")
class HandoffFilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: HomeRecordDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun writesTheHouseAsAHomerecordThatReadsBack() = runBlocking { // AC-F51
        db.homeDao().upsert(HomeEntity(id = "h1", name = "12 Hale St", createdAt = 1, updatedAt = 1))
        db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main floor", createdAt = 1, updatedAt = 1))
        db.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
        val files = HandoffFiles(context, db, zone = { ZoneId.of("America/New_York") })
        val home = files.load("h1")
        assertThat(home).isNotNull()
        val file = files.write(checkNotNull(home), "Hale kitchen")
        assertThat(file?.parentFile?.name).isEqualTo(HandoffFiles.DIR)
        assertThat(file?.name).matches("Hale kitchen \\d{4}-\\d{2}-\\d{2}\\.homerecord")
        val read = checkNotNull(file).inputStream().use { BundleReader.read(it, File(context.cacheDir, "unpack")) }
        val bundle = (read as BundleReadResult.Ok).bundle
        assertThat(HandoffSummary.of(bundle).rooms).isEqualTo(1)
        assertThat(bundle.source).isEqualTo("field-log")
    }

    @Test fun aHouseThatIsGoneLoadsAsNull() = runBlocking { // AC-F51
        assertThat(HandoffFiles(context, db).load("nope")).isNull()
    }
}

package com.kurtz.homerecord.fieldlog.handoff

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.bundle.BundleReadResult
import com.kurtz.homerecord.core.bundle.BundleReader
import com.kurtz.homerecord.core.bundle.HandoffApi
import com.kurtz.homerecord.core.bundle.HandoffSendFailure
import com.kurtz.homerecord.core.bundle.HandoffSendResult
import com.kurtz.homerecord.core.bundle.HandoffSummary
import com.kurtz.homerecord.core.bundle.SentHandoff
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobEntity
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

    private val sent = mutableListOf<List<String?>>()
    private val api = HandoffApi { companyId, jobId, email, homeName, bundle ->
        sent += listOf(companyId, jobId, email, homeName, bundle.name)
        HandoffSendResult.Sent(SentHandoff(email, 1L))
    }
    private fun files(company: String? = "co1", zone: () -> ZoneId = ZoneId::systemDefault) = HandoffFiles(context, db, api, { company }, zone)

    private suspend fun job(id: String, homeId: String, status: String = "ACTIVE", address: String? = null, clientId: String? = null) =
        db.jobDao().upsert(JobEntity(id = id, companyId = "co1", homeId = homeId, clientId = clientId, name = "Hale kitchen", address = address, status = status, createdAt = 1, updatedAt = 1))

    @Test fun theRecipientIsTheActiveJobOnTheHouseWithItsClientsEmail() = runBlocking { // AC-F51
        db.clientDao().upsert(ClientEntity(id = "c1", companyId = "co1", name = "Dana Hale", email = " dana@example.com ", createdAt = 1, updatedAt = 1))
        job("j-old", "h1", status = "DONE")
        job("j1", "h1", address = "12 Hale St", clientId = "c1")
        job("j2", "h2")
        assertThat(files().recipient("h1")).isEqualTo(HandoffFiles.Recipient("j1", "12 Hale St", "dana@example.com"))
        assertThat(files().recipient("h9")).isNull()
        assertThat(files(company = null).recipient("h1")).isNull()
    }

    @Test fun sendNamesTheHomeByTheJobsAddressOrElseTheJobName() = runBlocking { // AC-F51
        job("j1", "h1", address = "12 Hale St")
        job("j2", "h2")
        val file = File(context.cacheDir, "x.homerecord")
        files().send("h1", "Hale kitchen", "dana@example.com", file)
        files().send("h2", "Garage subpanel", "dana@example.com", file)
        assertThat(sent).containsExactly(
            listOf("co1", "j1", "dana@example.com", "12 Hale St", "x.homerecord"),
            listOf("co1", "j2", "dana@example.com", "Garage subpanel", "x.homerecord"),
        ).inOrder()
        assertThat(files(company = null).send("h1", "Hale kitchen", "a@b.co", file))
            .isEqualTo(HandoffSendResult.Failed(HandoffSendFailure.Failed))
    }

    @Test fun writesTheHouseAsAHomerecordThatReadsBack() = runBlocking { // AC-F51
        db.homeDao().upsert(HomeEntity(id = "h1", name = "12 Hale St", createdAt = 1, updatedAt = 1))
        db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main floor", createdAt = 1, updatedAt = 1))
        db.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
        val files = files(zone = { ZoneId.of("America/New_York") })
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
        assertThat(files().load("nope")).isNull()
    }
}

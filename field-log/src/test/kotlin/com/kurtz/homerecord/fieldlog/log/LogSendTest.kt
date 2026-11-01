package com.kurtz.homerecord.fieldlog.log

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.NoteEntity
import com.kurtz.homerecord.core.log.DailyLog
import com.kurtz.homerecord.core.log.DailyLogRepository
import com.kurtz.homerecord.core.model.LogWeather
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.pages.PageSource
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A sender that writes nothing: records what was made and opened, and can fail either step. */
private class FakeLogSender(var recipients: List<String> = listOf("jen.hale@example.com", "office@ortizbuild.com")) : LogSender {
    var makeFails = false
    var noApp = false
    val made = mutableListOf<DailyLog>()
    val opened = mutableListOf<Boolean>()

    override suspend fun recipients(jobId: String) = recipients

    override suspend fun make(log: DailyLog): LogPdfFile? {
        if (makeFails) return null
        made += log
        return LogPdfFile(File("/cache/logs/Daily log Hale kitchen ${log.day}.pdf"), recipients, "Hale kitchen: Daily log")
    }

    override fun open(pdf: LogPdfFile, email: Boolean): Boolean {
        if (noApp) return false
        opened += email
        return true
    }
}

// AC-F33 Send: email to the client and office addresses or the share sheet; Sent with time and recipients; re-send
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LogSendTest {
    private lateinit var db: HomeRecordDatabase
    private val day = LocalDate.of(2026, 10, 30)
    private val zone = ZoneOffset.UTC
    private var clock = day.atTime(17, 0).toInstant(zone).toEpochMilli()
    private val gates = MutableStateFlow(RoleGates(CompanyRole.Owner))
    private lateinit var repo: DailyLogRepository
    private val sender = FakeLogSender()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DailyLogRepository(
            db, weatherApi = { _, _, _ -> ApiResult.Ok(LogWeather(highF = 58, lowF = 41, conditions = "rain", fetchedAt = 1)) },
            now = { clock }, zone = { zone },
        )
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", lat = 40.06, lng = -75.4, createdAt = 1, updatedAt = 1))
            // written at 9 AM on the day, so the log has something in it and Send goes straight to the sheet
            val nine = day.atTime(9, 0).toInstant(zone).toEpochMilli()
            db.noteDao().upsert(NoteEntity(id = "n1", jobId = "j1", day = day.toString(), text = "Set the base cabinets", createdAt = nine, updatedAt = nine))
        }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(s: LogSender? = sender) = LogViewModel(LogDeps("j1", repo, db.photoDao(), gates, today = { day }, sender = s), SavedStateHandle())

    private suspend fun LogViewModel.settled(check: (LogUiState) -> Boolean): LogUiState =
        withTimeout(5_000) { state.first { !it.loading && check(it) } }

    @AcceptanceCriterion("AC-F33")
    @Test fun emailingTheLogMarksItSentWithTheTimeAndRecipients() = runBlocking<Unit> {
        val v = vm()
        assertThat(v.settled { it.exists }.canSend).isTrue()
        v.onEvent(LogEvent.OpenSend)
        val open = v.settled { it.editor is LogEditor.Send }
        assertThat((open.editor as LogEditor.Send).recipients).containsExactly("jen.hale@example.com", "office@ortizbuild.com").inOrder()

        v.onEvent(LogEvent.SendEmail)
        val sent = v.settled { it.sentAt != null }
        assertThat(sender.opened).containsExactly(true)
        assertThat(sent.sentAt).isEqualTo(clock)
        assertThat(sent.sentTo).containsExactly("jen.hale@example.com", "office@ortizbuild.com").inOrder()
        assertThat(sent.editor).isNull()
        val row = checkNotNull(repo.observe("j1", day).first())
        assertThat(row.sent).isTrue()
        assertThat(row.pdfPath).endsWith("Daily log Hale kitchen 2026-10-30.pdf")
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun theShareSheetMarksItSentWithNoRecipientsAndASecondSendReplacesTheFirst() = runBlocking<Unit> {
        val v = vm()
        v.settled { it.exists }
        v.onEvent(LogEvent.OpenSend)
        v.settled { it.editor is LogEditor.Send }
        v.onEvent(LogEvent.SendShare)
        val shared = v.settled { it.sentAt != null }
        assertThat(sender.opened).containsExactly(false)
        assertThat(shared.sentTo).isEmpty()

        val first = clock
        clock += 3_600_000
        v.onEvent(LogEvent.OpenSend)
        v.settled { it.editor is LogEditor.Send }
        v.onEvent(LogEvent.SendEmail)
        val again = v.settled { it.sentAt != first }
        assertThat(again.sentAt).isEqualTo(clock)
        assertThat(again.sentTo).hasSize(2)
        assertThat(sender.made).hasSize(2)
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun withNoEmailsOnTheJobEmailFallsBackToTheShareSheet() = runBlocking<Unit> {
        sender.recipients = emptyList()
        val v = vm()
        v.settled { it.exists }
        v.onEvent(LogEvent.OpenSend)
        assertThat((v.settled { it.editor is LogEditor.Send }.editor as LogEditor.Send).recipients).isEmpty()
        v.onEvent(LogEvent.SendEmail)
        assertThat(v.settled { it.sentAt != null }.sentTo).isEmpty()
        assertThat(sender.opened).containsExactly(false)
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun aFailedPdfOrNoAppKeepsTheSheetOpenWithTheErrorAndTheLogUnsent() = runBlocking<Unit> {
        val v = vm()
        v.settled { it.exists }
        v.onEvent(LogEvent.OpenSend)
        v.settled { it.editor is LogEditor.Send }
        sender.makeFails = true
        v.onEvent(LogEvent.SendEmail)
        val failed = v.settled { it.sendFailed }
        assertThat(failed.editor).isInstanceOf(LogEditor.Send::class.java)
        assertThat(failed.sentAt).isNull()

        sender.makeFails = false
        sender.noApp = true
        v.onEvent(LogEvent.SendShare)
        assertThat(v.settled { it.sendFailed && !it.sending }.sentAt).isNull()
        assertThat(checkNotNull(repo.observe("j1", day).first()).sent).isFalse()

        v.onEvent(LogEvent.DismissEditor)
        assertThat(v.settled { it.editor == null }.sendFailed).isFalse()
    }

    /** QA judge [fl-log-send-empty]: a day with nothing on it asks before the Send sheet; Cancel sends nothing. */
    @AcceptanceCriterion("AC-F33")
    @Test fun aDayWithNothingLoggedAsksBeforeSendingAndCancelSendsNothing() = runBlocking<Unit> {
        runBlocking { db.jobDao().upsert(JobEntity(id = "j2", companyId = "c1", name = "Garage subpanel", lat = 40.06, lng = -75.4, createdAt = 1, updatedAt = 1)) }
        val v = LogViewModel(LogDeps("j2", repo, db.photoDao(), gates, today = { day }, sender = sender), SavedStateHandle())
        val empty = v.settled { it.exists }
        assertThat(empty.nothingLogged).isTrue()
        v.onEvent(LogEvent.OpenSend)
        assertThat(v.settled { it.editor != null }.editor).isEqualTo(LogEditor.SendEmpty)
        // the sheet's buttons do nothing behind the warning
        v.onEvent(LogEvent.SendShare)
        assertThat(sender.made).isEmpty()
        v.onEvent(LogEvent.DismissEditor)
        assertThat(v.settled { it.editor == null }.sentAt).isNull()
        assertThat(sender.made).isEmpty()

        v.onEvent(LogEvent.OpenSend)
        v.settled { it.editor == LogEditor.SendEmpty }
        v.onEvent(LogEvent.SendAnyway)
        v.settled { it.editor is LogEditor.Send }
        v.onEvent(LogEvent.SendShare)
        assertThat(v.settled { it.sentAt != null }.sentTo).isEmpty()
        assertThat(sender.made).hasSize(1)
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun sendAnywayDoesNothingUnlessTheWarningIsOpen() = runBlocking<Unit> {
        val v = vm()
        assertThat(v.settled { it.exists }.nothingLogged).isFalse() // the day has a note
        v.onEvent(LogEvent.SendAnyway)
        assertThat(v.state.value.editor).isNull()
        v.onEvent(LogEvent.OpenSend)
        assertThat(v.settled { it.editor != null }.editor).isInstanceOf(LogEditor.Send::class.java)
    }

    @AcceptanceCriterion("AC-F33", "AC-F3")
    @Test fun crewReadTheLogButCannotSendIt() = runBlocking<Unit> {
        gates.value = RoleGates(CompanyRole.Crew)
        val v = vm()
        // Crew never build a draft, so save one as the lead would
        repo.assemble("j1", day)
        val s = v.settled { it.exists }
        assertThat(s.canSend).isFalse()
        v.onEvent(LogEvent.OpenSend)
        v.onEvent(LogEvent.SendEmail)
        assertThat(v.state.value.editor).isNull()
        assertThat(sender.made).isEmpty()
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun previewMakesThePdfForTheSavedLogAndRetryTriesAgain() = runBlocking<Unit> {
        val pages = object : PageSource {
            override val pageCount = 2
            override suspend fun render(index: Int, widthPx: Int) = null
        }
        var opens = 0
        val failing = FakeLogSender().apply { makeFails = true }
        val none = LogPreviewViewModel(LogPreviewDeps("j1", day, repo, failing, open = { opens++; pages }))
        assertThat(withTimeout(5_000) { none.state.first { !it.loading } }.failed).isTrue() // no log saved for the day yet

        repo.assemble("j1", day)
        val p = LogPreviewViewModel(LogPreviewDeps("j1", day, repo, failing, open = { opens++; pages }))
        assertThat(withTimeout(5_000) { p.state.first { !it.loading } }.failed).isTrue()
        failing.makeFails = false
        p.onEvent(LogPreviewEvent.Retry)
        val shown = withTimeout(5_000) { p.state.first { !it.loading } }
        assertThat(shown.failed).isFalse()
        assertThat(shown.pages?.pageCount).isEqualTo(2)
        assertThat(failing.made.single().day).isEqualTo(day)
        assertThat(opens).isEqualTo(1)
    }
}

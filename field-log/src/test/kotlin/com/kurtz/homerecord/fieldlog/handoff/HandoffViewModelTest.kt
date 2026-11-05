package com.kurtz.homerecord.fieldlog.handoff

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.bundle.BundleRecord
import com.kurtz.homerecord.core.bundle.ExportedHome
import com.kurtz.homerecord.core.bundle.HandoffSendFailure
import com.kurtz.homerecord.core.bundle.HandoffSendResult
import com.kurtz.homerecord.core.bundle.HomeBundle
import com.kurtz.homerecord.core.bundle.SentHandoff
import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import java.time.ZoneId
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

/** AC-F51: FL-Handoff counts the job's house and writes the `.homerecord` for the share sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
@AcceptanceCriterion("AC-F51")
class HandoffViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun rec(id: String) = BundleRecord(buildJsonObject { put("id", id); put("createdAt", 1L); put("updatedAt", 1L) })

    private fun home(rooms: Int) = ExportedHome(
        HomeBundle(source = "field-log", exportedAt = 1L, homeId = "h1", records = mapOf("room" to List(rooms) { rec("r$it") })),
        emptyMap(),
    )

    @Test fun loadsTheSummaryOfTheJobsHouse() { // AC-F51
        val vm = HandoffViewModel("Hale kitchen", load = { home(3) }, write = { null })
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.jobName).isEqualTo("Hale kitchen")
        assertThat(s.summary?.rooms).isEqualTo(3)
    }

    @Test fun exportReadsTheHouseAgainAndHandsTheFileToTheShareSheet() { // AC-F51
        var rooms = 1
        val written = mutableListOf<ExportedHome>()
        val vm = HandoffViewModel("Hale kitchen", load = { home(rooms) }, write = { written += it; File("/cache/handoff/Hale kitchen 2026-11-05.homerecord") })
        rooms = 2
        vm.onEvent(HandoffEvent.Export)
        assertThat(written.single().bundle.count("room")).isEqualTo(2)
        assertThat(vm.state.value.sharePath).endsWith("Hale kitchen 2026-11-05.homerecord")
        assertThat(vm.state.value.summary?.rooms).isEqualTo(2)
        vm.onEvent(HandoffEvent.ShareHandled)
        assertThat(vm.state.value.sharePath).isNull()
    }

    @Test fun aFileThatCannotBeWrittenSaysSoAndExportCanBeTriedAgain() { // AC-F51
        var fail = true
        val vm = HandoffViewModel("Hale kitchen", load = { home(1) }, write = { if (fail) error("disk full") else File("/cache/handoff/x.homerecord") })
        vm.onEvent(HandoffEvent.Export)
        assertThat(vm.state.value.failed).isTrue()
        assertThat(vm.state.value.exporting).isFalse()
        fail = false
        vm.onEvent(HandoffEvent.Export)
        assertThat(vm.state.value.failed).isFalse()
        assertThat(vm.state.value.sharePath).isNotNull()
    }

    @Test fun anEmptyHouseOrAMissingOneIsNotExported() { // AC-F51
        var writes = 0
        val empty = HandoffViewModel("Garage subpanel", load = { home(0) }, write = { writes++; File("x") })
        empty.onEvent(HandoffEvent.Export)
        val gone = HandoffViewModel("Garage subpanel", load = { null }, write = { writes++; File("x") })
        gone.onEvent(HandoffEvent.Export)
        assertThat(writes).isEqualTo(0)
        assertThat(gone.state.value.missing).isTrue()
    }

    private val utc = { ZoneId.of("UTC") }
    private val dec5 = LocalDate.of(2026, 12, 5).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() + 3_600_000

    private fun sender(
        saved: SavedStateHandle = SavedStateHandle(),
        prefill: String? = "dana@example.com",
        rooms: () -> Int = { 2 },
        send: suspend (String, File) -> HandoffSendResult = { email, _ -> HandoffSendResult.Sent(SentHandoff(email, dec5)) },
    ) = HandoffViewModel(
        "Hale kitchen", load = { home(rooms()) }, write = { File.createTempFile("handoff", ".homerecord") },
        send = send, saved = saved, prefillEmail = { prefill }, zone = utc,
    )

    @Test fun sendToHomeownerPrefillsTheClientsEmailAndSaysWhereItWentAndUntilWhen() { // AC-F51
        val to = mutableListOf<String>()
        val vm = sender(send = { email, file -> to += email; assertThat(file.exists()).isTrue(); HandoffSendResult.Sent(SentHandoff(email, dec5)) })
        assertThat(vm.state.value.email).isEqualTo("dana@example.com")
        vm.onEvent(HandoffEvent.EmailChanged(" Dana.Hale@Example.com "))
        vm.onEvent(HandoffEvent.Send)
        assertThat(to).containsExactly("dana.hale@example.com")
        val s = vm.state.value
        assertThat(s.sending).isFalse()
        assertThat(s.sentTo).isEqualTo("dana.hale@example.com")
        assertThat(s.sentUntil).isEqualTo(LocalDate.of(2026, 12, 5))
        // A second tap does not send twice; Send another clears the sent state.
        vm.onEvent(HandoffEvent.Send)
        assertThat(to).hasSize(1)
        vm.onEvent(HandoffEvent.SendAnother)
        assertThat(vm.state.value.sentTo).isNull()
        assertThat(vm.state.value.email).isEqualTo(" Dana.Hale@Example.com ")
    }

    @Test fun aBadAddressIsCaughtBeforeAnythingIsSent() { // AC-F51
        var sends = 0
        val vm = sender(prefill = null, send = { e, _ -> sends++; HandoffSendResult.Sent(SentHandoff(e, dec5)) })
        assertThat(vm.state.value.email).isEmpty()
        vm.onEvent(HandoffEvent.Send)
        assertThat(vm.state.value.emailInvalid).isTrue()
        vm.onEvent(HandoffEvent.EmailChanged("dana@example"))
        assertThat(vm.state.value.emailInvalid).isFalse()
        vm.onEvent(HandoffEvent.Send)
        assertThat(vm.state.value.emailInvalid).isTrue()
        assertThat(sends).isEqualTo(0)
    }

    @Test fun offlineAndTooLargeAreToldAndSendCanBeTriedAgain() { // AC-F51
        var result: HandoffSendResult = HandoffSendResult.Failed(HandoffSendFailure.Offline)
        val vm = sender(send = { _, _ -> result })
        vm.onEvent(HandoffEvent.Send)
        assertThat(vm.state.value.sendFailure).isEqualTo(HandoffSendFailure.Offline)
        assertThat(vm.state.value.sending).isFalse()
        result = HandoffSendResult.Failed(HandoffSendFailure.TooLarge)
        vm.onEvent(HandoffEvent.Send)
        assertThat(vm.state.value.sendFailure).isEqualTo(HandoffSendFailure.TooLarge)
        result = HandoffSendResult.Sent(SentHandoff("dana@example.com", dec5))
        vm.onEvent(HandoffEvent.Send)
        assertThat(vm.state.value.sendFailure).isNull()
        assertThat(vm.state.value.sentTo).isEqualTo("dana@example.com")
    }

    @Test fun theTypedAddressAndAFinishedSendSurviveProcessDeath() { // AC-F51
        val saved = SavedStateHandle()
        val first = sender(saved = saved, prefill = "dana@example.com")
        first.onEvent(HandoffEvent.EmailChanged("owner@halehome.com"))
        first.onEvent(HandoffEvent.Send)
        val restored = sender(saved = saved, prefill = "dana@example.com")
        assertThat(restored.state.value.email).isEqualTo("owner@halehome.com")
        assertThat(restored.state.value.sentTo).isEqualTo("owner@halehome.com")
        assertThat(restored.state.value.sentUntil).isEqualTo(LocalDate.of(2026, 12, 5))
    }

    @Test fun anEmptyHouseIsNotSent() { // AC-F51
        var sends = 0
        val vm = sender(rooms = { 0 }, send = { e, _ -> sends++; HandoffSendResult.Sent(SentHandoff(e, dec5)) })
        vm.onEvent(HandoffEvent.Send)
        assertThat(sends).isEqualTo(0)
    }
}

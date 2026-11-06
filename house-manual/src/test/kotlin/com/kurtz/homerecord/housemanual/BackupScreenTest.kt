package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.bundle.HomeBackup
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.backup.BackupEvent
import com.kurtz.homerecord.housemanual.backup.BackupFiles
import com.kurtz.homerecord.housemanual.backup.BackupPassphrase
import com.kurtz.homerecord.housemanual.backup.BackupScreen
import com.kurtz.homerecord.housemanual.backup.BackupUiState
import com.kurtz.homerecord.housemanual.backup.BackupViewModel
import com.kurtz.homerecord.housemanual.backup.MadeBackup
import com.kurtz.homerecord.housemanual.backup.PassphraseProblem
import com.kurtz.homerecord.housemanual.importing.ImportError
import com.kurtz.homerecord.housemanual.importing.ImportRead
import com.kurtz.homerecord.housemanual.importing.RestoreEvent
import com.kurtz.homerecord.housemanual.importing.RestoreScreen
import com.kurtz.homerecord.housemanual.importing.RestoreUiState
import com.kurtz.homerecord.housemanual.importing.RestoreViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// HM-Backup and HM-Restore (T074b): the passphrase rule, Make backup and share, and the restore passphrase prompt.
// The backup AC claims come with T074c (H16 flow and the AC-H14 round trip).
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackupScreenTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `a passphrase needs eight characters typed the same twice`() {
        assertThat(BackupPassphrase.problem("maple87", "maple87")).isEqualTo(PassphraseProblem.TooShort)
        assertThat(BackupPassphrase.problem("maple-street", "maple-stret")).isEqualTo(PassphraseProblem.Mismatch)
        assertThat(BackupPassphrase.problem("maple-street", "maple-street")).isNull()
    }

    @Test fun `the file is named for the home and the day`() {
        assertThat(BackupFiles.fileName("Maple Street house", LocalDate.of(2026, 11, 6))).isEqualTo("Maple-Street-house-backup-2026-11-06.hrbackup")
        assertThat(BackupFiles.fileName("  ", LocalDate.of(2026, 11, 6))).isEqualTo("home-backup-2026-11-06.${HomeBackup.EXTENSION}")
    }

    @Test fun `make checks the passphrase first, then makes and hands the file to the share sheet`() {
        val asked = mutableListOf<String>()
        val result = CompletableDeferred<MadeBackup?>()
        val vm = BackupViewModel("Maple Street house") { asked += String(it); result.await() }
        vm.onEvent(BackupEvent.Make)
        assertThat(vm.state.value.problem).isEqualTo(PassphraseProblem.TooShort)
        vm.onEvent(BackupEvent.PassphraseChanged("maple-street-1987"))
        assertThat(vm.state.value.problem).isEqualTo(PassphraseProblem.Mismatch)
        vm.onEvent(BackupEvent.ConfirmChanged("maple-street-1987"))
        assertThat(vm.state.value.problem).isNull()
        assertThat(asked).isEmpty()

        vm.onEvent(BackupEvent.Make)
        assertThat(vm.state.value.busy).isTrue()
        vm.onEvent(BackupEvent.Make)
        assertThat(asked).containsExactly("maple-street-1987")
        result.complete(MadeBackup("content://files/exports/b.hrbackup", "Maple-Street-house-backup-2026-11-06.hrbackup"))
        assertThat(vm.state.value.busy).isFalse()
        assertThat(vm.state.value.uri).isEqualTo("content://files/exports/b.hrbackup")
        assertThat(vm.state.value.made).isEqualTo("Maple-Street-house-backup-2026-11-06.hrbackup")
        vm.onEvent(BackupEvent.ShareHandled)
        assertThat(vm.state.value.uri).isNull()
    }

    @Test fun `a failed write says so and keeps the passphrase for another try`() {
        val vm = BackupViewModel("Maple Street house") { null }
        vm.onEvent(BackupEvent.PassphraseChanged("maple-street-1987"))
        vm.onEvent(BackupEvent.ConfirmChanged("maple-street-1987"))
        vm.onEvent(BackupEvent.Make)
        assertThat(vm.state.value.failed).isTrue()
        assertThat(vm.state.value.passphrase).isEqualTo("maple-street-1987")
    }

    @Test fun `the screen shows the mismatch, a 56 dp make button and restore`() {
        val events = mutableListOf<BackupEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual) {
                BackupScreen(BackupUiState(homeName = "Maple Street house", passphrase = "maple-street", confirm = "maple", problem = PassphraseProblem.Mismatch)) { events += it }
            }
        }
        rule.onNodeWithText("The two passphrases don't match.").assertExists()
        rule.onNodeWithTag("hm_backup_make").assertHeightIsAtLeast(56.dp).performClick()
        rule.onNodeWithTag("hm_backup_passphrase").performTextInput("x")
        rule.onNodeWithTag("hm_backup_restore").performScrollTo().assertHeightIsAtLeast(56.dp).performClick()
        assertThat(events.first()).isEqualTo(BackupEvent.Make)
        assertThat(events.filterIsInstance<BackupEvent.PassphraseChanged>().single().value).contains("x")
        assertThat(events.last()).isEqualTo(BackupEvent.Restore)
    }

    @Test fun `restore keeps a wrong passphrase on screen and opens the preview on the right one`() {
        val tried = mutableListOf<String>()
        val vm = RestoreViewModel { pass ->
            tried += String(pass)
            if (String(pass) == "maple-street-1987") {
                ImportRead.Ok(
                    com.kurtz.homerecord.housemanual.importing.ReadImport(
                        com.kurtz.homerecord.core.bundle.ImportPreview(
                            com.kurtz.homerecord.core.bundle.HomeBundle(source = "house-manual", exportedAt = 0, homeId = "h", records = emptyMap()),
                            com.kurtz.homerecord.core.bundle.ImportPlanner.plan(
                                com.kurtz.homerecord.core.bundle.HomeBundle(source = "house-manual", exportedAt = 0, homeId = "h", records = emptyMap()),
                                emptyMap(), emptyMap(),
                            ),
                        ),
                        java.io.File("unused"),
                    ),
                )
            } else {
                ImportRead.Failed(ImportError.WrongPassphrase)
            }
        }
        vm.onEvent(RestoreEvent.Open)
        assertThat(tried).isEmpty()
        vm.onEvent(RestoreEvent.PassphraseChanged("maple-street-1978"))
        vm.onEvent(RestoreEvent.Open)
        assertThat(vm.state.value.error).isEqualTo(ImportError.WrongPassphrase)
        assertThat(vm.opened.value).isNull()
        vm.onEvent(RestoreEvent.PassphraseChanged("maple-street-1987"))
        assertThat(vm.state.value.error).isNull()
        vm.onEvent(RestoreEvent.Open)
        assertThat(vm.opened.value).isNotNull()
        assertThat(vm.state.value.passphrase).isEmpty()
        assertThat(tried).containsExactly("maple-street-1978", "maple-street-1987").inOrder()
    }

    @Test fun `the restore prompt words a wrong passphrase and disables Open while empty`() {
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual) {
                RestoreScreen(RestoreUiState(passphrase = "", error = ImportError.WrongPassphrase)) {}
            }
        }
        rule.onNodeWithText("That passphrase doesn't open this backup. Check it and try again.").assertExists()
        rule.onNodeWithTag("hm_restore_open").assertHeightIsAtLeast(56.dp).assertIsNotEnabled()
    }
}

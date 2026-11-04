package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.SignatureStroke
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sign on phone (AC-F38): the pad sends strokes, Clear and Sign; a Signed order shows the signer and Duplicate. */
@AcceptanceCriterion("AC-F38", "AC-F40")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ChangeOrderSignScreenTest {
    @get:Rule val rule = createComposeRule()

    private val base = ChangeOrderSignUiState(loading = false, title = "Island outlet", totalCents = 18_500, lineCount = 1, signerName = "Dana Hale")
    private val ink = SignatureStroke(listOf(0.1f, 0.5f, 0.5f, 0.6f, 0.8f, 0.4f))

    @Test fun drawingOnThePadSendsAStrokeAndSignStaysOffWhileBlank() {
        val events = mutableListOf<ChangeOrderSignEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { ChangeOrderSignScreen(base, { events += it }) } }
        rule.onNodeWithText("Total $185.00 · 1 line").assertExists()
        rule.onNodeWithTag("fl_co_sign_sign").assertIsNotEnabled()
        rule.onNodeWithTag("fl_co_sign_clear").assertIsNotEnabled()
        rule.onNodeWithTag("fl_co_sign_pad").performTouchInput { swipeRight() }
        val stroke = events.filterIsInstance<ChangeOrderSignEvent.Stroke>().single().stroke
        assertThat(stroke.pointCount).isAtLeast(2)
        assertThat(stroke.points.all { it in 0f..1f }).isTrue()
    }

    @Test fun signAndClearSendTheirEventsOnceDrawn() {
        val events = mutableListOf<ChangeOrderSignEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { ChangeOrderSignScreen(base.copy(strokes = listOf(ink)), { events += it }) } }
        rule.onNodeWithText("By signing, Dana Hale approves this change order for $185.00.").assertExists()
        rule.onNodeWithTag("fl_co_sign_sign").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("fl_co_sign_clear").performClick()
        assertThat(events).containsExactly(ChangeOrderSignEvent.Sign, ChangeOrderSignEvent.Clear).inOrder()
    }

    @Test fun signedFormShowsSignerAndDuplicateAndNoSignButton() {
        val draft = ChangeOrderFormDraft("Island outlet", "Dana Hale", lines = listOf(FormLine("l1", "Outlet", "1", "185")))
        val events = mutableListOf<ChangeOrderFormEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                CompositionLocalProvider(LocalTagPrefix provides "fl") {
                    ChangeOrderFormScreen(
                        ChangeOrderFormUiState(isNew = false, loading = false, locked = true, draft = draft, initial = draft, signature = CoSignature("Dana Hale", 1_793_808_000_000L, null)),
                        { events += it },
                    )
                }
            }
        }
        rule.onNodeWithTag("fl_co_form_sign").assertDoesNotExist()
        rule.onNodeWithTag("fl_co_form_delete").assertDoesNotExist()
        rule.onNodeWithTag("fl_co_form_signature").performScrollTo()
        rule.onNodeWithText("Signed by Dana Hale", substring = true).assertExists()
        rule.onNodeWithTag("fl_co_form_duplicate").performScrollTo().performClick()
        assertThat(events).containsExactly(ChangeOrderFormEvent.Duplicate)
    }

    @Test fun draftFormSignsOnlyWithAPricedLine() {
        val events = mutableListOf<ChangeOrderFormEvent>()
        val draft = ChangeOrderFormDraft("Island outlet", "Dana Hale", lines = listOf(FormLine("l1", "Outlet", "1", "185")))
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                ChangeOrderFormScreen(ChangeOrderFormUiState(isNew = false, loading = false, draft = draft, initial = draft), { events += it })
            }
        }
        rule.onNodeWithTag("fl_co_form_sign").performScrollTo().assertIsEnabled().performClick()
        assertThat(events).containsExactly(ChangeOrderFormEvent.SignOnPhone)
    }

    // AC-F40: Owner/Office see Send link to sign; while a link is on it says so and Turn off link asks first (AC-B35).
    @Test fun linkSectionSendsAndTurnsOffAndHidesWithoutTheRole() {
        val events = mutableListOf<ChangeOrderFormEvent>()
        val draft = ChangeOrderFormDraft("Island outlet", "Dana Hale", lines = listOf(FormLine("l1", "Outlet", "1", "185")))
        var state by androidx.compose.runtime.mutableStateOf(ChangeOrderFormUiState(isNew = false, loading = false, draft = draft, initial = draft))
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { ChangeOrderFormScreen(state, { events += it }) }
        }
        rule.onNodeWithTag("fl_co_form_share_link").assertDoesNotExist()
        state = state.copy(linkAllowed = true)
        rule.onNodeWithTag("fl_co_form_share_link").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithText("Send link to sign").assertExists()
        rule.onNodeWithTag("fl_co_form_link_off").assertDoesNotExist()
        state = state.copy(linkUrl = "https://api.example.test/share/co/tok")
        rule.onNodeWithTag("fl_co_form_link_on").performScrollTo()
        rule.onNodeWithText("Link is on. Anyone with it can read this order and sign it.").assertExists()
        rule.onNodeWithTag("fl_co_form_link_off").performScrollTo().performClick()
        state = state.copy(confirmLinkOff = true)
        rule.onNodeWithTag("fl_co_form_link_off_confirm").performClick()
        assertThat(events).containsExactly(ChangeOrderFormEvent.ShareLink, ChangeOrderFormEvent.AskLinkOff, ChangeOrderFormEvent.LinkOff).inOrder()
    }
}

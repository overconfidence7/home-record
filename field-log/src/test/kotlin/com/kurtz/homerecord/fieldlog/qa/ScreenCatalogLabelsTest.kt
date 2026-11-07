package com.kurtz.homerecord.fieldlog.qa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S4 accessibility: on every ScreenCatalog entry, every control TalkBack can activate has something to read,
 * its own content description or the text it merges from its children (AC-U10, ui-standards rule on
 * content descriptions). An icon-only button with contentDescription = null fails here, naming the entry and tag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ScreenCatalogLabelsTest {
    @get:Rule val rule = createComposeRule()

    private fun SemanticsNode.label(): String = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
        config.getOrNull(SemanticsProperties.Text)?.joinToString(),
        config.getOrNull(SemanticsProperties.EditableText)?.text,
    ).joinToString().trim()

    @AcceptanceCriterion("AC-U10")
    @Test fun everyClickableControlOnEveryCatalogScreenHasALabel() {
        val entries = ScreenCatalog.entries
        var index by mutableIntStateOf(0)
        rule.setContent { key(index) { entries[index].content() } }
        val unlabeled = mutableListOf<String>()
        for (i in entries.indices) {
            index = i
            rule.waitForIdle()
            rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
                .filter { it.label().isEmpty() }
                .forEach { unlabeled += "${entries[i].id}: ${it.config.getOrNull(SemanticsProperties.TestTag) ?: "(no tag)"}" }
        }
        assertThat(unlabeled.distinct()).isEmpty()
    }
}

package com.kurtz.homerecord.housemanual.items

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.ItemDetailLookupNotePreview
import com.kurtz.homerecord.housemanual.ItemDetailNoWarrantyPreview
import com.kurtz.homerecord.housemanual.ItemDetailPreview
import com.kurtz.homerecord.housemanual.ItemDetailRecallsPreview
import com.kurtz.homerecord.housemanual.ItemDetailReviewBadgePreview
import com.kurtz.homerecord.housemanual.ItemDetailUpkeepPreview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AC-U4: every clickable node on the item page is at least 48 dp, judged like the device ScreenCrawlerTest
 * (a node cut by the viewport is judged by its layout size). Native graphics so text has real widths.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi", application = android.app.Application::class)
@AcceptanceCriterion("AC-U4")
class ItemDetailTouchTargetTest {
    @get:Rule val rule = createComposeRule()

    private fun smallNodes(content: @Composable () -> Unit): List<String> {
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            content()
        }
        val min = 48 * density - 1
        return rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { n ->
            val t = n.touchBoundsInRoot
            val clippedW = t.width < n.size.width - 1
            val clippedH = t.height < n.size.height - 1
            maxOf(t.width, if (clippedW) n.size.width.toFloat() else 0f) < min ||
                maxOf(t.height, if (clippedH) n.size.height.toFloat() else 0f) < min
        }.map { it.describe(density) }
    }

    private fun SemanticsNode.describe(density: Float): String {
        val tag = config.getOrNull(SemanticsProperties.TestTag)
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        return "${tag ?: "?"} \"${text ?: ""}\" ${(size.width / density).toInt()}x${(size.height / density).toInt()} dp touch ${(touchBoundsInRoot.width / density).toInt()}x${(touchBoundsInRoot.height / density).toInt()} dp"
    }

    private fun assertTargets(id: String, content: @Composable () -> Unit) {
        val small = smallNodes(content)
        assertTrue("$id nodes under 48 dp: $small", small.isEmpty())
    }

    @Test fun itemPage() = assertTargets("item-detail") { ItemDetailPreview() }

    @Test fun itemPageNoWarranty() = assertTargets("item-detail-no-warranty") { ItemDetailNoWarrantyPreview() }

    @Test fun itemPageLookupFailed() = assertTargets("item-detail-lookup-failed") { ItemDetailLookupNotePreview() }

    @Test fun itemPageUpkeep() = assertTargets("item-detail-upkeep") { ItemDetailUpkeepPreview() }

    @Test fun itemPageRecalls() = assertTargets("item-detail-recalls") { ItemDetailRecallsPreview() }

    @Test fun itemPageReviewBadge() = assertTargets("item-detail-review-badge") { ItemDetailReviewBadgePreview() }
}

package com.kurtz.homerecord.fieldlog.log

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** QA F09b: a saved section is brought back into view above the pinned Send bar. Viewport 0..1000 px. */
class LogSectionRevealTest {
    private fun item(key: String, index: Int, offset: Int, size: Int = 120) = RevealItem(key, index, offset, size)

    @Test fun savedTextBelowTheBottomEdgeScrollsJustEnough() {
        val visible = listOf(item("weather", 10, 500), item("visitors-h", 11, 820), item("visitors", 12, 940))
        assertThat(LogSectionReveal.target(LogTextSection.VISITORS, 1, visible, 0, 1000)).isEqualTo(RevealTarget.ScrollBy(60))
    }

    @Test fun headerOnTheEdgeWithTextOutOfViewPutsTheHeaderAtTheTop() {
        val visible = listOf(item("weather", 10, 700), item("visitors-h", 11, 900))
        assertThat(LogSectionReveal.target(LogTextSection.VISITORS, 1, visible, 0, 1000)).isEqualTo(RevealTarget.ToItem(11))
    }

    @Test fun aSectionAlreadyInViewIsLeftAlone() {
        val visible = listOf(item("safety-h", 13, 300), item("safety", 14, 420))
        assertThat(LogSectionReveal.target(LogTextSection.SAFETY, 1, visible, 0, 1000)).isEqualTo(RevealTarget.None)
    }

    @Test fun neverPushesTheHeaderAboveTheTop() {
        val visible = listOf(item("client-h", 15, 40), item("client", 16, 160, size = 1200))
        assertThat(LogSectionReveal.target(LogTextSection.CLIENT_NOTES, 1, visible, 0, 1000)).isEqualTo(RevealTarget.ScrollBy(40))
    }

    @Test fun workDoneRevealsItsLastEntryOrTheNoneLine() {
        val entries = listOf(item("work-h", 2, 600), item("work-0", 3, 720), item("work-1", 4, 840, size = 200))
        assertThat(LogSectionReveal.target(LogTextSection.WORK_DONE, 2, entries, 0, 1000)).isEqualTo(RevealTarget.ScrollBy(40))
        val none = listOf(item("work-h", 2, 950))
        assertThat(LogSectionReveal.target(LogTextSection.WORK_DONE, 0, none, 0, 1000)).isEqualTo(RevealTarget.ToItem(2))
    }

    @Test fun aSectionScrolledAwayEntirelyIsNotChased() {
        val visible = listOf(item("crew-h", 0, 0), item("work-h", 2, 200))
        assertThat(LogSectionReveal.target(LogTextSection.SAFETY, 1, visible, 0, 1000)).isEqualTo(RevealTarget.None)
    }
}

package com.kurtz.homerecord.fieldlog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** judge:fl-markup-nav-bar, judge:fl-capture-nav-bar: editors and the camera hide the tab bar (rules 3 and 30). */
class FullScreenKeysTest {
    @Test fun editorsAndCaptureAreFullScreen() {
        listOf(MarkupKey("f"), AnnotateKey("p"), CaptureKey("j"), LogWeatherKey("j", "2026-10-31"), JobEditKey(null), CompanyKey)
            .forEach { assertTrue("$it", isFullScreen(it)) }
    }

    @Test fun viewersAndListsKeepTheTabBar() {
        listOf(JobKey("j"), FileKey("f"), PhotoKey("p"), TeamKey, TimesheetsKey, ReportsKey, JobRoomKey("r"))
            .forEach { assertFalse("$it", isFullScreen(it)) }
    }
}

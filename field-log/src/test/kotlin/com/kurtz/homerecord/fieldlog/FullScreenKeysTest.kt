package com.kurtz.homerecord.fieldlog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** judge:fl-markup-nav-bar, judge:fl-capture-nav-bar: editors and the camera hide the tab bar (rules 3 and 30). */
class FullScreenKeysTest {
    @Test fun editorsAndCaptureAreFullScreen() {
        listOf(MarkupKey("f"), AnnotateKey("p"), CaptureKey("j"), LogWeatherKey("j", "2026-10-31"), JobEditKey(null), CompanyKey, MaterialFormKey(null), MaterialFormKey("m"), JobItemEditKey("h"), JobItemEditKey("h", "i"))
            .forEach { assertTrue("$it", isFullScreen(it)) }
    }

    @Test fun viewersAndListsKeepTheTabBar() {
        listOf(JobKey("j"), FileKey("f"), PhotoKey("p"), TeamKey, TimesheetsKey(), ReportsKey, ClientsKey, MaterialsKey, JobRoomKey("r"), JobItemKey("h", "i"), JobItemPlaceKey("h", "i"), JobHandoffKey("h", "Hale kitchen"))
            .forEach { assertFalse("$it", isFullScreen(it)) }
    }
}

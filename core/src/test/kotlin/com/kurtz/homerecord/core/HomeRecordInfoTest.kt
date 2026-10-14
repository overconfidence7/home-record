package com.kurtz.homerecord.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRecordInfoTest {
    @Test
    fun bundleExtensionIsHomerecord() {
        assertEquals("homerecord", HomeRecordInfo.BUNDLE_EXTENSION)
    }
}

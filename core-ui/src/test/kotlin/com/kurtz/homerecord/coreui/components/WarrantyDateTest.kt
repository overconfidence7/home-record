package com.kurtz.homerecord.coreui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/** The warranty line's date: one formatter per locale, kept between rows (AC-U11), never stale after a locale change. */
class WarrantyDateTest {
    @Test fun formatsInTheGivenLocaleAndFollowsALocaleChange() {
        assertThat(displayDate("2029-06-15", Locale.US)).isEqualTo("Jun 15, 2029")
        assertThat(displayDate("2029-06-15", Locale.US)).isEqualTo("Jun 15, 2029")
        assertThat(displayDate("2029-06-15", Locale.GERMANY)).isEqualTo("15.06.2029")
        assertThat(displayDate("2029-06-15", Locale.US)).isEqualTo("Jun 15, 2029")
    }

    @Test fun keepsTextThatIsNotADate() {
        assertThat(displayDate("someday", Locale.US)).isEqualTo("someday")
    }
}

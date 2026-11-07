package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S1a: count strings read "1 month", never "1 months". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CountPluralsTest {
    private val r = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun monthsItemsAndCharacters() {
        assertThat(r.getQuantityString(R.plurals.item_detail_warranty_months, 1, 1)).isEqualTo("1 month")
        assertThat(r.getQuantityString(R.plurals.item_detail_warranty_months, 12, 12)).isEqualTo("12 months")
        assertThat(r.getQuantityString(R.plurals.reminders_repeat_months, 1, 1)).isEqualTo("Every month")
        assertThat(r.getQuantityString(R.plurals.reminders_repeat_months, 3, 3)).isEqualTo("Every 3 months")
        assertThat(r.getQuantityString(R.plurals.items_no_match_body, 1, 1)).isEqualTo("Nothing fits those filters. Clear them to see the 1 item.")
        assertThat(r.getQuantityString(R.plurals.backup_passphrase_short, 8, 8)).isEqualTo("Use at least 8 characters.")
    }
}

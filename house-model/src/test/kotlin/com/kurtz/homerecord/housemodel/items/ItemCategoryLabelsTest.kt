package com.kurtz.homerecord.housemodel.items

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// cr:label-strings: category names come from strings.xml; code keys on the enum
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemCategoryLabelsTest {
    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    @Test @AcceptanceCriterion("AC-H6")
    fun everyCategoryHasItsOwnNonBlankName() {
        val names = ItemCategory.entries.map { res.getString(it.labelRes) }
        assertThat(names.none { it.isBlank() }).isTrue()
        assertThat(names.toSet()).hasSize(ItemCategory.entries.size)
        assertThat(res.getString(ItemCategory.HEATING_COOLING.labelRes)).isEqualTo("Heating and cooling")
    }

    @Test fun aStoredIdTheAppDoesNotKnowReadsAsOther() {
        assertThat(ItemCategory.orOther("SHUTOFFS")).isEqualTo(ItemCategory.SHUTOFFS)
        assertThat(ItemCategory.orOther("POOLS")).isEqualTo(ItemCategory.OTHER)
    }
}

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

// cr:seed-strings: item type names and the upkeep a new item seeds come from strings.xml, keyed by :core's catalog
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemSubtypeLabelsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test @AcceptanceCriterion("AC-C29")
    fun everyCatalogKeyHasItsOwnString() {
        val all = ItemCategory.all()
        assertThat(subtypeLabelKeys).containsExactlyElementsIn(all.map { it.id })
        assertThat(consumableSpecKeys).containsExactlyElementsIn(all.flatMap { s -> s.consumables.map { it.specKey } }.toSet())
        assertThat(reminderTitleKeys).containsExactlyElementsIn(all.flatMap { s -> s.reminders.map { it.titleKey } }.toSet())
        assertThat(all.map { resources.getString(it.labelRes) }.toSet()).hasSize(all.size)
    }

    @Test fun furnaceReadsAndSeedsInEnglish() {
        val furnace = checkNotNull(ItemCategory.subtype("furnace"))
        assertThat(resources.getString(furnace.labelRes)).isEqualTo("Furnace")
        assertThat(furnace.consumables.map { resources.getString(it.specRes) }).containsExactly("Furnace filter")
        assertThat(furnace.reminders.map { resources.getString(it.titleRes) }).containsExactly("Replace furnace filter", "HVAC service").inOrder()
        val erv = checkNotNull(ItemCategory.subtype("erv_hrv"))
        assertThat(resources.getString(erv.labelRes)).isEqualTo("ERV/HRV")
        assertThat(erv.reminders.map { resources.getString(it.titleRes) }).containsExactly("Replace ERV core filter")
    }
}

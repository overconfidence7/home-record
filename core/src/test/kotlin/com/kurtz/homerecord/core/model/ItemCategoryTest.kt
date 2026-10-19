package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ItemCategoryTest {
    @Test fun hasTwelveCategories() {
        assertThat(ItemCategory.entries).hasSize(12)
    }

    @Test fun subtypeCountsMatchSpec() {
        assertThat(ItemCategory.APPLIANCES.subtypes).hasSize(12)
        assertThat(ItemCategory.HEATING_COOLING.subtypes).hasSize(12)
        assertThat(ItemCategory.WATER.subtypes).hasSize(13)
        assertThat(ItemCategory.ELECTRICAL.subtypes).hasSize(7)
        assertThat(ItemCategory.SHUTOFFS.subtypes).hasSize(9)
        assertThat(ItemCategory.SAFETY.subtypes).hasSize(7)
        assertThat(ItemCategory.FIXTURES.subtypes).hasSize(6)
        assertThat(ItemCategory.DOORS_OPENERS.subtypes).hasSize(5)
        assertThat(ItemCategory.EXTERIOR.subtypes).hasSize(8)
        assertThat(ItemCategory.FINISHES.subtypes).hasSize(6)
        assertThat(ItemCategory.NETWORKING.subtypes).hasSize(6)
    }

    @Test fun subtypeIdsAreUniqueAndEveryOneHasAnIcon() {
        val all = ItemCategory.all()
        assertThat(all.map { it.id }.toSet()).hasSize(all.size)
        assertThat(all.all { it.iconName.isNotBlank() }).isTrue()
    }

    @Test fun furnaceDefaultsFilterAndServiceReminders() {
        val furnace = ItemCategory.subtype("furnace")!!
        assertThat(furnace.consumables.map { it.kind }).containsExactly(ConsumableKind.FILTER)
        assertThat(furnace.reminders.map { it.intervalMonths }).containsExactly(3, 12).inOrder()
    }

    @Test fun maintenanceDefaultsMatchSpec() {
        fun months(id: String) = ItemCategory.subtype(id)!!.reminders.map { it.intervalMonths }
        assertThat(months("water_heater")).containsExactly(12)
        assertThat(months("water_softener")).containsExactly(2)
        assertThat(months("smoke_detector")).containsExactly(12)
        assertThat(months("dryer")).containsExactly(12)
        assertThat(months("gutters")).containsExactly(6)
        assertThat(months("sump_pump")).containsExactly(3)
    }

    @Test fun lookupAcrossCategories() {
        assertThat(ItemCategory.subtype("water_main")!!.category).isEqualTo(ItemCategory.SHUTOFFS)
        assertThat(ItemCategory.subtype("nope")).isNull()
    }
}

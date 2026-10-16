package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.EquipmentCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EquipmentRuleTest {
    private val today = LocalDate.of(2026, 10, 16)

    @Test fun categoryIsTheOnlyRequirement() {
        assertFalse(EquipmentRule.canSave(null))
        assertTrue(EquipmentRule.canSave(EquipmentCategory.OTHER))
    }

    @Test fun dateValidation() {
        assertTrue(EquipmentRule.isValidDate(""))
        assertTrue(EquipmentRule.isValidDate(" "))
        assertTrue(EquipmentRule.isValidDate("2020-02-29"))
        assertFalse(EquipmentRule.isValidDate("2021-02-29"))
        assertFalse(EquipmentRule.isValidDate("2021-2-9"))
        assertFalse(EquipmentRule.isValidDate("tomorrow"))
    }

    @Test fun ageIsWholeYearsFloored() {
        assertEquals(5, AgeAndWarrantyFormatter.ageYears("2020-10-17", today))
        assertEquals(6, AgeAndWarrantyFormatter.ageYears("2020-10-16", today))
        assertEquals(0, AgeAndWarrantyFormatter.ageYears("2026-01-01", today))
    }

    @Test fun ageNullWhenUnsetBadOrFuture() {
        assertNull(AgeAndWarrantyFormatter.ageYears(null, today))
        assertNull(AgeAndWarrantyFormatter.ageYears("garbage", today))
        assertNull(AgeAndWarrantyFormatter.ageYears("2030-01-01", today))
    }

    @Test fun warrantyUntilOrExpired() {
        assertNull(AgeAndWarrantyFormatter.warranty(null, today))
        assertEquals(WarrantyStatus.Until("2027-01-01"), AgeAndWarrantyFormatter.warranty("2027-01-01", today))
        assertEquals(WarrantyStatus.Until("2026-10-16"), AgeAndWarrantyFormatter.warranty("2026-10-16", today))
        assertEquals(WarrantyStatus.Expired, AgeAndWarrantyFormatter.warranty("2026-10-15", today))
    }
}

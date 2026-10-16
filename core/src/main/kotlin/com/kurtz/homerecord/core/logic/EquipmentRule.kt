package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.EquipmentCategory
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** AC-43: only the category is required; every other field is optional. */
object EquipmentRule {
    fun canSave(category: EquipmentCategory?): Boolean = category != null

    /** True for a blank (no date) or a valid ISO `yyyy-MM-dd` string. */
    fun isValidDate(value: String): Boolean = value.isBlank() || parseDate(value.trim()) != null

    fun parseDate(value: String): LocalDate? = try {
        if (value.length == 10) LocalDate.parse(value) else null
    } catch (_: DateTimeParseException) {
        null
    }
}

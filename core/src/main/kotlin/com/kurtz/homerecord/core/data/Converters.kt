package com.kurtz.homerecord.core.data

import androidx.room.TypeConverter

/** Room stores enums by name on its own; only the slot list needs a converter. */
class Converters {
    @TypeConverter
    fun slotsToString(slots: List<Int>): String = slots.joinToString(",")

    @TypeConverter
    fun stringToSlots(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(",").mapNotNull { it.trim().toIntOrNull() }
}

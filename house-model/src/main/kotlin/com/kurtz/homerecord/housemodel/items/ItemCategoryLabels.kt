package com.kurtz.homerecord.housemodel.items

import androidx.annotation.StringRes
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.housemodel.R

/** The category's display name; :core keeps only the enum, so lists, filters and counts key on it, not on English text. */
@get:StringRes
val ItemCategory.labelRes: Int
    get() = when (this) {
        ItemCategory.APPLIANCES -> R.string.item_category_appliances
        ItemCategory.HEATING_COOLING -> R.string.item_category_heating_cooling
        ItemCategory.WATER -> R.string.item_category_water
        ItemCategory.ELECTRICAL -> R.string.item_category_electrical
        ItemCategory.SHUTOFFS -> R.string.item_category_shutoffs
        ItemCategory.SAFETY -> R.string.item_category_safety
        ItemCategory.FIXTURES -> R.string.item_category_fixtures
        ItemCategory.DOORS_OPENERS -> R.string.item_category_doors_openers
        ItemCategory.EXTERIOR -> R.string.item_category_exterior
        ItemCategory.FINISHES -> R.string.item_category_finishes
        ItemCategory.NETWORKING -> R.string.item_category_networking
        ItemCategory.OTHER -> R.string.item_category_other
    }

/** A stored category id as the enum; an id from a newer app version reads as Other. */
fun ItemCategory.Companion.orOther(id: String): ItemCategory = fromId(id) ?: ItemCategory.OTHER

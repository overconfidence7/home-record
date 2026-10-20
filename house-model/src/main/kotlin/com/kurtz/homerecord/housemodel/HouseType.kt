package com.kurtz.homerecord.housemodel

/** House styles offered when creating a home (AC-C1). [name] is what Room stores in `home.houseType`. */
enum class HouseType {
    RANCHER, TWO_STORY, SPLIT_LEVEL, RAISED_RANCH, CAPE_COD, COLONIAL, TOWNHOUSE, BUNGALOW, OTHER;

    companion object {
        fun parse(value: String): HouseType = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/** Foundation choices (AC-C1). [name] is what Room stores in `home.foundation`. */
enum class Foundation {
    BASEMENT, WALKOUT_BASEMENT, CRAWLSPACE, SLAB;

    val hasBasement: Boolean get() = this == BASEMENT || this == WALKOUT_BASEMENT

    companion object {
        fun parse(value: String): Foundation = entries.firstOrNull { it.name == value } ?: BASEMENT
    }
}

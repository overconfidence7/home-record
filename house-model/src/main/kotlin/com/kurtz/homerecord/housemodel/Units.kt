package com.kurtz.homerecord.housemodel

/** Lengths are stored as whole inches. */
object Units {
    const val INCHES_PER_FOOT = 12
    fun feetToInches(feet: Int): Int = feet * INCHES_PER_FOOT
}

package com.kurtz.homerecord.housemodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnitsTest {
    @Test
    fun feetConvertToInches() {
        assertThat(Units.feetToInches(10)).isEqualTo(120)
    }
}

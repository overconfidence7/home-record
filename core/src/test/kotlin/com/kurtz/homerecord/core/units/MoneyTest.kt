package com.kurtz.homerecord.core.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {
    @Test fun parsesDollarsAndCents() {
        assertThat(Money.parseCents("24.99")).isEqualTo(2499L)
        assertThat(Money.parseCents("$1,200")).isEqualTo(120000L)
        assertThat(Money.parseCents("5.5")).isEqualTo(550L)
        assertThat(Money.parseCents(" 42 ")).isEqualTo(4200L)
    }

    @Test fun rejectsBadInput() {
        assertThat(Money.parseCents("")).isNull()
        assertThat(Money.parseCents("abc")).isNull()
        assertThat(Money.parseCents("1.234")).isNull()
        assertThat(Money.parseCents("-3")).isNull()
        assertThat(Money.parseCents("99999999999999999999")).isNull()
    }

    @Test fun formatsCents() {
        assertThat(Money.format(2499)).isEqualTo("24.99")
        assertThat(Money.format(5)).isEqualTo("0.05")
        assertThat(Money.parseCents(Money.format(129_950))).isEqualTo(129_950L)
    }
}

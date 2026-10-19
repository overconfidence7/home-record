package com.kurtz.homerecord.coreui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyParsingTest {
    @Test fun parsesDollarsAndCents() {
        assertThat(parseCents("24.99")).isEqualTo(2499L)
        assertThat(parseCents("$1,200")).isEqualTo(120000L)
        assertThat(parseCents("5.5")).isEqualTo(550L)
    }

    @Test fun rejectsBadInput() {
        assertThat(parseCents("")).isNull()
        assertThat(parseCents("abc")).isNull()
        assertThat(parseCents("1.234")).isNull()
        assertThat(parseCents("-3")).isNull()
    }

    @Test fun formatsCents() {
        assertThat(formatCents(2499)).isEqualTo("24.99")
        assertThat(formatCents(5)).isEqualTo("0.05")
    }
}

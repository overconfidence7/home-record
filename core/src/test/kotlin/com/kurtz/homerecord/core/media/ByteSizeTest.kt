package com.kurtz.homerecord.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class ByteSizeTest {
    @Test fun labelsUseDecimalUnits() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertThat(ByteSize.label(0)).isEqualTo("0 B")
            assertThat(ByteSize.label(999)).isEqualTo("999 B")
            assertThat(ByteSize.label(1_000)).isEqualTo("1 KB")
            assertThat(ByteSize.label(482_000)).isEqualTo("482 KB")
            assertThat(ByteSize.label(2_400_000)).isEqualTo("2.4 MB")
            assertThat(ByteSize.label(3_250_000_000)).isEqualTo("3.3 GB")
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun negativeSizeNeverShowsAMinus() {
        assertThat(ByteSize.label(-5)).isEqualTo("0 B")
    }
}

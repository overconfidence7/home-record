package com.kurtz.homerecord.coreui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrandTest {
    @Test fun brandsHaveDistinctPrimaries() {
        for (dark in listOf(false, true)) {
            assertThat(brandColorScheme(Brand.HouseManual, dark).primary)
                .isNotEqualTo(brandColorScheme(Brand.FieldLog, dark).primary)
        }
    }

    @Test fun lightAndDarkDiffer() {
        for (b in Brand.entries) {
            assertThat(brandColorScheme(b, false).primary).isNotEqualTo(brandColorScheme(b, true).primary)
        }
    }
}

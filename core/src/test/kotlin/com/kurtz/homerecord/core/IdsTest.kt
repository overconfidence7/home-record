package com.kurtz.homerecord.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IdsTest {
    @Test
    fun newIdIsUniqueUuidString() {
        val a = Ids.newId()
        val b = Ids.newId()
        assertThat(a).isNotEqualTo(b)
        assertThat(a).hasLength(36)
    }
}

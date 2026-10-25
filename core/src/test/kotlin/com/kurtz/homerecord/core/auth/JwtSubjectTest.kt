package com.kurtz.homerecord.core.auth

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import org.junit.Test

// AC-F8: the clock needs the signed-in person's id to attribute punches.
class JwtSubjectTest {
    private fun token(payload: String) =
        "h." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".s"

    @Test fun readsSubject() = assertThat(JwtSubject.of(token("""{"sub":"u-42","exp":1}"""))).isEqualTo("u-42")

    @Test fun missingSubjectOrBadTokenIsNull() {
        assertThat(JwtSubject.of(token("""{"exp":1}"""))).isNull()
        assertThat(JwtSubject.of("garbage")).isNull()
        assertThat(JwtSubject.of("a.!!!.c")).isNull()
    }
}

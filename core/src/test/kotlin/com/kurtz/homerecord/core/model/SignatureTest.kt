package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-F38: a tap or a dot is not a signature; strokes round-trip so the same signature draws on the pad, PNG and PDF.
@AcceptanceCriterion("AC-F38")
class SignatureTest {
    @Test fun aBlankPadOrATapIsNotSigned() {
        assertThat(Signature.isSigned(emptyList())).isFalse()
        assertThat(Signature.isSigned(listOf(SignatureStroke(listOf(0.5f, 0.5f))))).isFalse()
        assertThat(Signature.isSigned(listOf(SignatureStroke(listOf(0.5f, 0.5f, 0.52f, 0.51f))))).isFalse()
    }

    @Test fun inkAddsUpAcrossStrokes() {
        val a = SignatureStroke(listOf(0.1f, 0.5f, 0.2f, 0.5f))
        val b = SignatureStroke(listOf(0.3f, 0.5f, 0.42f, 0.5f))
        assertThat(Signature.inkLength(listOf(a, b))).isWithin(1e-4f).of(0.22f)
        assertThat(Signature.isSigned(listOf(a))).isFalse()
        assertThat(Signature.isSigned(listOf(a, b))).isTrue()
    }

    @Test fun heightCountsAtThePadsAspect() {
        val down = SignatureStroke(listOf(0.5f, 0.1f, 0.5f, 0.6f))
        assertThat(Signature.inkLength(listOf(down), aspect = 0.4f)).isWithin(1e-4f).of(0.2f)
        assertThat(Signature.isSigned(listOf(down), aspect = 0.3f)).isFalse()
    }

    @Test fun strokesRoundTripAndBadTextIsBlank() {
        val strokes = listOf(SignatureStroke(listOf(0.1f, 0.2f, 0.3f, 0.4f)), SignatureStroke(listOf(0.6f, 0.6f)))
        assertThat(Signature.decode(Signature.encode(strokes))).isEqualTo(strokes)
        assertThat(Signature.decode("not json")).isEmpty()
        assertThat(Signature.decode(null)).isEmpty()
        assertThat(Signature.clamp(-0.2f)).isEqualTo(0f)
        assertThat(Signature.clamp(1.4f)).isEqualTo(1f)
    }
}

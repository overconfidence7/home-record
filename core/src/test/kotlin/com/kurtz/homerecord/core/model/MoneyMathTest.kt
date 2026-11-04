package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-F37: change order line items with quantity and price, total in cents (MoneyMath)
@AcceptanceCriterion("AC-F37")
class MoneyMathTest {
    @Test fun lineIsQuantityTimesPriceRoundedHalfUpToTheCent() {
        assertThat(MoneyMath.lineCents(3_000, 4_250)).isEqualTo(12_750)
        assertThat(MoneyMath.lineCents(2_500, 8_550)).isEqualTo(21_375)
        // 0.125 × $0.99 = 12.375 cents → 12; 0.135 × $0.99 = 13.365 → 13; 1.5 × $0.33 = 49.5 → 50
        assertThat(MoneyMath.lineCents(125, 99)).isEqualTo(12)
        assertThat(MoneyMath.lineCents(1_500, 33)).isEqualTo(50)
    }

    @Test fun totalIsTheSumOfTheRoundedLines() {
        val lines = listOf(
            ChangeOrderLineItem("a", "Extra outlet", 2_000, 18_500),
            ChangeOrderLineItem("b", "Labor", 1_500, 33),
            ChangeOrderLineItem("c", "Permit", 1_000, 7_500),
        )
        assertThat(MoneyMath.totalCents(lines)).isEqualTo(37_000 + 50 + 7_500)
        assertThat(MoneyMath.totalCents(emptyList())).isEqualTo(0)
    }

    @Test fun quantitiesParseToThousandthsAndFormatBack() {
        assertThat(MoneyMath.parseQuantity("3")).isEqualTo(3_000)
        assertThat(MoneyMath.parseQuantity("2.5")).isEqualTo(2_500)
        assertThat(MoneyMath.parseQuantity("0.125")).isEqualTo(125)
        listOf("", "0", "-1", "1.2345", "x").forEach { assertThat(MoneyMath.parseQuantity(it)).isNull() }
        assertThat(MoneyMath.formatQuantity(2_500)).isEqualTo("2.5")
        assertThat(MoneyMath.formatQuantity(3_000)).isEqualTo("3")
        assertThat(MoneyMath.formatQuantity(125)).isEqualTo("0.125")
    }

    @Test fun linesRoundTripThroughJsonAndBadJsonReadsAsNoLines() {
        val lines = listOf(ChangeOrderLineItem("a", "Move outlet", 1_000, 18_500), ChangeOrderLineItem("b", "Drywall patch", 2_500, 6_000))
        assertThat(ChangeOrderLineItem.decode(ChangeOrderLineItem.encode(lines))).isEqualTo(lines)
        assertThat(ChangeOrderLineItem.decode("not json")).isEmpty()
        assertThat(ChangeOrderLineItem.decode(null)).isEmpty()
        assertThat(ChangeOrderStatus.of("SIGNED")).isEqualTo(ChangeOrderStatus.SIGNED)
        assertThat(ChangeOrderStatus.of("bogus")).isEqualTo(ChangeOrderStatus.DRAFT)
    }
}

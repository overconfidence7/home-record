package com.kurtz.homerecord.core.material

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-F44 (the amount is suggested from the receipt total when one is found)
@AcceptanceCriterion("AC-F44")
class ReceiptTotalTest {
    @Test fun findsTheTotalAndSkipsSubtotalAndTax() {
        val lines = listOf("CITY ELECTRIC SUPPLY", "12/2 NM-B 250FT   84.10", "WAGO 221 50PK   31.25", "SUBTOTAL   115.35", "SALES TAX 7.25%   8.36", "TOTAL   $123.71", "VISA   123.71")
        assertThat(ReceiptTotal.find(lines)).isEqualTo(12_371L)
    }

    @Test fun readsTheAmountOnTheNextLineWhenTheRowWasSplit() {
        assertThat(ReceiptTotal.find(listOf("Subtotal 40.00", "Amount due", "$1,042.50", "Thank you"))).isEqualTo(104_250L)
        assertThat(ReceiptTotal.find(listOf("BALANCE DUE  19,99"))).isEqualTo(1_999L)
    }

    @Test fun noTotalLineIsNoSuggestion() {
        assertThat(ReceiptTotal.find(listOf("HOME DEPOT", "2x4x8 SPF   4.98", "Subtotal 4.98", "Tax 0.37"))).isNull()
        assertThat(ReceiptTotal.find(emptyList())).isNull()
        assertThat(ReceiptTotal.find(listOf("Total items: 3"))).isNull()
    }

    @Test fun theLastTotalWins() {
        assertThat(ReceiptTotal.find(listOf("Total 10.00", "Coupon -2.00", "Grand total 8.00"))).isEqualTo(800L)
    }
}

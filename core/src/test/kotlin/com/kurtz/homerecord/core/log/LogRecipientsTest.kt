package com.kurtz.homerecord.core.log

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

class LogRecipientsTest {
    @AcceptanceCriterion("AC-F33")
    @Test fun clientFirstThenOfficeWithoutBlanksBadAddressesOrRepeats() {
        val to = LogRecipients.of(
            " dana@halehome.com ",
            listOf("office@ortizelectric.com", "", "not an email", "Dana@HaleHome.com", "pm@ortizelectric.com", "office@ortizelectric.com"),
        )
        assertThat(to).containsExactly("dana@halehome.com", "office@ortizelectric.com", "pm@ortizelectric.com").inOrder()
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun noClientEmailAndNoOfficeMeansNoOne() {
        assertThat(LogRecipients.of(null, emptyList())).isEmpty()
        assertThat(LogRecipients.of("  ", listOf("office@"))).isEmpty()
    }
}

package com.kurtz.homerecord.fieldlog.log

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-F30: the crew section of the daily log is editable; hours typed by hand read the way a lead writes them
class LogHoursTest {
    @AcceptanceCriterion("AC-F30")
    @Test fun readsDecimalColonAndHourMinuteForms() {
        assertThat(LogHours.minutes("7.5")).isEqualTo(450)
        assertThat(LogHours.minutes("7,25")).isEqualTo(435)
        assertThat(LogHours.minutes("8")).isEqualTo(480)
        assertThat(LogHours.minutes(".5")).isEqualTo(30)
        assertThat(LogHours.minutes("7:30")).isEqualTo(450)
        assertThat(LogHours.minutes("7h 30m")).isEqualTo(450)
        assertThat(LogHours.minutes("7H")).isEqualTo(420)
        assertThat(LogHours.minutes("45m")).isEqualTo(45)
        assertThat(LogHours.minutes(" 0 ")).isEqualTo(0)
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun rejectsWhatIsNotATimeOrMoreThanADay() {
        listOf("", ".", "abc", "7:75", "7h 60m", "25", "-2", "1500m", "7.5.1").forEach {
            assertThat(LogHours.minutes(it)).isNull()
        }
    }

    @Test fun savedMinutesShowAsDecimalHoursThatReadBack() {
        assertThat(LogHours.text(450)).isEqualTo("7.5")
        assertThat(LogHours.text(480)).isEqualTo("8")
        assertThat(LogHours.text(440)).isEqualTo("7.33")
        listOf(0, 1, 59, 95, 440, 465, 1440).forEach { assertThat(LogHours.minutes(LogHours.text(it))).isEqualTo(it) }
    }
}

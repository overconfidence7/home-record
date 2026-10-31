package com.kurtz.homerecord.fieldlog.today

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

/** Which job a photo taken from Today's camera button is tagged to (AC-F24). */
@AcceptanceCriterion("AC-F24")
class TodayCaptureTest {
    private val kitchen = TodayJob("j1", "Kitchen Remodel", "412 Maple Ave")
    private val deck = TodayJob("j2", "Deck Rebuild", "88 Orchard Ln")
    private val garage = TodayJob("j3", "Garage subpanel", null)
    private val live = listOf(kitchen, deck, garage)

    @Test fun theJobImClockedInOnWins() {
        assertThat(TodayCapture.choose("j2", listOf(kitchen), live)).isEqualTo(TodayCaptureChoice(target = "j2"))
    }

    @Test fun myOnlyJobTodayIsNextWhenClockedOut() {
        assertThat(TodayCapture.choose(null, listOf(deck), live).target).isEqualTo("j2")
    }

    @Test fun theCompanysOnlyLiveJobNeedsNoQuestion() {
        assertThat(TodayCapture.choose(null, emptyList(), listOf(garage)).target).isEqualTo("j3")
    }

    @Test fun otherwiseItAsksWithMyJobsTodayFirst() {
        val choice = TodayCapture.choose(null, listOf(garage, deck), live)
        assertThat(choice.target).isNull()
        assertThat(choice.options.map { it.id }).containsExactly("j3", "j2", "j1").inOrder()
        assertThat(choice.available).isTrue()
    }

    @Test fun aShiftOnAJobThatIsNoLongerLiveDoesNotCount() {
        assertThat(TodayCapture.choose("gone", emptyList(), live).options.map { it.id }).containsExactly("j1", "j2", "j3").inOrder()
    }

    @Test fun noLiveJobMeansNoButton() {
        assertThat(TodayCapture.choose("j1", listOf(kitchen), emptyList()).available).isFalse()
    }
}

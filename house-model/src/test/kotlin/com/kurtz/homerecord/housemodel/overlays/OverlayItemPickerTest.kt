package com.kurtz.homerecord.housemodel.overlays

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C26: the linked-item picker lists the wall's room first and narrows by name or room.
class OverlayItemPickerTest {
    private val heater = OverlayItemChoice("i1", "Water heater", "basement", "Basement")
    private val dishwasher = OverlayItemChoice("i2", "Dishwasher", "kitchen", "Kitchen")
    private val disposal = OverlayItemChoice("i3", "Disposal", "kitchen", "Kitchen")
    private val panel = OverlayItemChoice("i4", "Main panel", null, null)
    private val all = listOf(heater, panel, disposal, dishwasher)

    @Test
    @AcceptanceCriterion("AC-C26")
    fun theWallsRoomComesFirstThenTheRestByName() {
        assertThat(OverlayItemPicker.filter(all, "", "kitchen")).containsExactly(dishwasher, disposal, panel, heater).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun withNoRoomEverythingIsByNameIgnoringCase() {
        val lower = OverlayItemChoice("i5", "attic fan", null, null)
        assertThat(OverlayItemPicker.filter(all + lower, "  ", null)).containsExactly(lower, dishwasher, disposal, panel, heater).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun everyWordMustMatchTheNameOrTheRoom() {
        assertThat(OverlayItemPicker.filter(all, "KITCHEN", null)).containsExactly(dishwasher, disposal).inOrder()
        assertThat(OverlayItemPicker.filter(all, "dis kitchen", null)).containsExactly(dishwasher, disposal).inOrder()
        assertThat(OverlayItemPicker.filter(all, "water base", null)).containsExactly(heater)
        assertThat(OverlayItemPicker.filter(all, "water kitchen", null)).isEmpty()
    }
}

package com.kurtz.homerecord.housemodel.plan

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C6 (a room named in the editor carries its kind to the room page, walkthrough H3), [judge:hm-room-kind-not-inferred]
@AcceptanceCriterion("AC-C6")
class RoomKindGuessTest {
    @Test fun namesGiveTheirKind() {
        assertThat(RoomKindGuess.guess("Kitchen")).isEqualTo(RoomKind.KITCHEN)
        assertThat(RoomKindGuess.guess("living room")).isEqualTo(RoomKind.LIVING_ROOM)
        assertThat(RoomKindGuess.guess("Half bath")).isEqualTo(RoomKind.BATHROOM)
        assertThat(RoomKindGuess.guess("Primary bedroom")).isEqualTo(RoomKind.BEDROOM)
        assertThat(RoomKindGuess.guess("Mudroom")).isEqualTo(RoomKind.MUDROOM)
        assertThat(RoomKindGuess.guess("Family room")).isEqualTo(RoomKind.LIVING_ROOM)
    }

    @Test fun theMoreSpecificWordWins() {
        assertThat(RoomKindGuess.guess("Bedroom closet")).isEqualTo(RoomKind.CLOSET)
        assertThat(RoomKindGuess.guess("Hall bath")).isEqualTo(RoomKind.BATHROOM)
        assertThat(RoomKindGuess.guess("Primary bath")).isEqualTo(RoomKind.BATHROOM)
    }

    @Test fun wordsMatchWholeAndUnknownNamesGiveNothing() {
        assertThat(RoomKindGuess.guess("Kitche")).isNull()
        assertThat(RoomKindGuess.guess("Denver room")).isNull()
        assertThat(RoomKindGuess.guess("")).isNull()
        assertThat(RoomKindGuess.guess("Room 3")).isNull()
    }

    @Test fun aKindThePersonPickedIsKept() {
        // Other, or the kind the old name gave, follows the new name; a picked kind stays.
        assertThat(RoomKindGuess.afterRename(RoomKind.OTHER, "", "Kitchen")).isEqualTo(RoomKind.KITCHEN)
        assertThat(RoomKindGuess.afterRename(RoomKind.KITCHEN, "Kitchen", "Office")).isEqualTo(RoomKind.OFFICE)
        assertThat(RoomKindGuess.afterRename(RoomKind.KITCHEN, "Kitchen", "Kitche")).isEqualTo(RoomKind.OTHER)
        assertThat(RoomKindGuess.afterRename(RoomKind.BEDROOM, "Room 3", "Kitchen")).isEqualTo(RoomKind.BEDROOM)
    }

    @Test fun typingANameInTheEditorSetsTheKind() {
        val e = PlanEditor(emptyList())
        val room = e.addRoom("a", "", RoomKind.OTHER, Pt(600, 400))
        "Kitchen".indices.forEach { i -> e.previewRename(room.id, "Kitchen".substring(0, i + 1)) }
        e.commit()
        assertThat(e.rooms.single().kind).isEqualTo("KITCHEN")
        e.setKind(room.id, RoomKind.DINING_ROOM)
        e.previewRename(room.id, "Kitchen nook")
        e.commit()
        assertThat(e.rooms.single().kind).isEqualTo("DINING_ROOM")
    }
}

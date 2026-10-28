package com.kurtz.homerecord.housemodel.plan

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// cr:wall-identity: a wall keeps its id (and its photos and overlays) through a reshape. AC-C17, AC-C27
@AcceptanceCriterion("AC-C17")
class WallMatcherTest {
    private val matcher = WallMatcher()
    private val room = PlanRoom.rectangle("r", "Kitchen", "KITCHEN", 0, 0, 144, 120)
    private val walls = listOf(SavedWall("north", 0), SavedWall("east", 1), SavedWall("south", 2), SavedWall("west", 3))

    @Test fun unchangedOutlineKeepsEveryWall() {
        val match = matcher.match(room.points, walls, room.points)
        assertThat(match.wallOfEdge).containsExactly(0, "north", 1, "east", 2, "south", 3, "west")
        assertThat(match.removed).isEmpty()
    }

    @Test fun draggingASideKeepsThatWallAndItsNeighbours() {
        val wider = room.resizedEdge(1, 168)
        val match = matcher.match(room.points, walls, wider.points)
        assertThat(match.wallOfEdge).containsExactly(0, "north", 1, "east", 2, "south", 3, "west")
    }

    @Test fun addingACornerShiftsLaterEdgesButNotTheirWalls() {
        // A corner on the north edge: edges after it move up one index, the walls follow their sides.
        val notched = room.withCornerOnEdge(0, Pt(72, 0))
        assertThat(notched.points).hasSize(5)
        val match = matcher.match(room.points, walls, notched.points)
        assertThat(match.wallOfEdge.filterValues { it != "north" }).containsExactly(2, "east", 3, "south", 4, "west")
        assertThat(match.wallOfEdge.values).contains("north")
        assertThat(match.wallOfEdge).hasSize(4)
        assertThat(match.removed).isEmpty()
    }

    @Test fun removingACornerDropsTheWallThatLostItsEdge() {
        val lShape = PlanRoom("r", "Den", points = listOf(Pt(0, 0), Pt(144, 0), Pt(144, 60), Pt(96, 60), Pt(96, 120), Pt(0, 120)))
        val saved = lShape.points.indices.map { SavedWall("w$it", it) }
        val cut = lShape.withoutCorner(3)
        val match = matcher.match(lShape.points, saved, cut.points)
        // The notch's two sides (w2, w3) become one diagonal edge, which gets a new wall.
        assertThat(match.wallOfEdge).containsExactly(0, "w0", 1, "w1", 3, "w4", 4, "w5")
        assertThat(match.removed).containsExactly("w2", "w3")
    }

    @Test fun movingTheRoomKeepsEveryWall() {
        val moved = room.moved(600, 480)
        val match = matcher.match(room.points, walls, moved.points)
        assertThat(match.wallOfEdge).containsExactly(0, "north", 1, "east", 2, "south", 3, "west")
    }

    @Test fun reversedWindingKeepsWallsOnTheirSides() {
        val reversed = room.points.reversed()
        val match = matcher.match(room.points, walls, reversed)
        val sideOf = reversed.indices.associateWith { i ->
            val a = reversed[i]
            val b = reversed[(i + 1) % reversed.size]
            when {
                a.y == 0 && b.y == 0 -> "north"
                a.x == 144 && b.x == 144 -> "east"
                a.y == 120 && b.y == 120 -> "south"
                else -> "west"
            }
        }
        assertThat(match.wallOfEdge).isEqualTo(sideOf)
    }

    @Test fun staleOrDuplicateWallsAreRemoved() {
        val fresh = matcher.match(emptyList(), emptyList(), room.points)
        assertThat(fresh.wallOfEdge).isEmpty()
        val stale = matcher.match(room.points, listOf(SavedWall("ghost", 9), SavedWall("north", 0), SavedWall("dup", 0)), room.points)
        assertThat(stale.wallOfEdge).containsExactly(0, "north")
        assertThat(stale.removed).containsExactly("ghost", "dup")
    }
}

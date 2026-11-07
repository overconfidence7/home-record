package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.housemodel.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S1a: count strings read "1 room", never "1 rooms". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CountPluralsTest {
    private val r = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun oneAndManyRooms() {
        assertThat(r.getQuantityString(R.plurals.floor_rooms, 1, 1)).isEqualTo("1 room")
        assertThat(r.getQuantityString(R.plurals.floor_rooms, 6, 6)).isEqualTo("6 rooms")
        assertThat(r.getQuantityString(R.plurals.floor_delete_body_rooms, 1, 1)).isEqualTo("The 1 room on this floor is removed too.")
        assertThat(r.getQuantityString(R.plurals.house3d_scene_description, 1, "Attic", 1)).isEqualTo("3D view of Attic: 1 room")
    }

    @Test
    fun wallsSlotsAndReportCounts() {
        assertThat(r.getQuantityString(R.plurals.behind_wall_room_progress, 1, 1, 1, 0)).isEqualTo("Rough-in 1 of 1 wall · Finished 0 of 1")
        assertThat(r.getQuantityString(R.plurals.behind_wall_room_progress, 4, 1, 4, 0)).isEqualTo("Rough-in 1 of 4 walls · Finished 0 of 4")
        assertThat(r.getQuantityString(R.plurals.house3d_walls_photographed, 1, 0, 1, "Finished")).isEqualTo("Finished photos on 0 of 1 wall")
        assertThat(r.getQuantityString(R.plurals.legend_slots, 40, 40)).isEqualTo("40 slots")
        val counts = r.getString(
            R.string.report_counts,
            r.getQuantityString(R.plurals.report_count_rooms, 1, 1),
            r.getQuantityString(R.plurals.report_count_items, 1, 1),
        )
        assertThat(counts).isEqualTo("1 room · 1 item")
    }
}

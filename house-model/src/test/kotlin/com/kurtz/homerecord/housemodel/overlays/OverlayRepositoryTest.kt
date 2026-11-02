package com.kurtz.homerecord.housemodel.overlays

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverlayRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: OverlayRepository
    private val changes = mutableListOf<String>()
    private var clock = 1_000L

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = OverlayRepository(db, now = { clock++ }, onChange = { e, id -> changes += "$e/$id" })
        db.wallDao().upsert(WallEntity(id = "w-north", roomId = "kitchen", edgeIndex = 0, orientation = "N", lengthIn = 168, heightIn = 96, createdAt = 1, updatedAt = 1))
    }

    @After fun tearDown() = db.close()

    private val supply = OverlayDraft(
        kind = OverlayKind.WATER_COLD,
        points = listOf(OverlayPoint(0.3f, 0.2f), OverlayPoint(0.3f, 1.2f)),
        stage = WallStage.ROUGH_IN,
        label = "  Sink supply ",
        note = " ",
        depth = OverlayDepth.BEHIND_DRYWALL,
    )

    @Test
    @AcceptanceCriterion("AC-C27")
    fun aWallOverlayKeepsWallSpacePointsAndTheWallsRoom() = runTest {
        val id = (repo.addOnWall("home", "w-north", supply) as OverlaySave.Saved).id
        val saved = repo.observeForWall("w-north").first().single()

        assertThat(saved.id).isEqualTo(id)
        assertThat(saved.roomId).isEqualTo("kitchen")
        assertThat(saved.label).isEqualTo("Sink supply")
        assertThat(saved.note).isNull()
        assertThat(saved.stage).isEqualTo(WallStage.ROUGH_IN)
        // Clamped onto the wall: 1.2 down is the floor line.
        assertThat(saved.points).containsExactly(OverlayPoint(0.3f, 0.2f), OverlayPoint(0.3f, 1f)).inOrder()
        assertThat(saved.shape).isEqualTo(OverlayShape.LINE)
        assertThat(repo.observeForRoom("kitchen").first().map { it.id }).containsExactly(id)
        assertThat(changes).containsExactly("overlay/$id")
    }

    @Test
    fun planOverlaysKeepPlanInches() = runTest {
        val gas = OverlayDraft(OverlayKind.GAS, listOf(OverlayPoint(-24f, 300f), OverlayPoint(120f, 300f)), WallStage.FINISHED, depth = OverlayDepth.IN_SLAB)
        val id = (repo.addOnPlan("home", roomId = null, draft = gas) as OverlaySave.Saved).id
        val saved = repo.observeForHome("home").first().single()
        assertThat(saved.id).isEqualTo(id)
        assertThat(saved.onWall).isFalse()
        assertThat(saved.points.first()).isEqualTo(OverlayPoint(-24f, 300f))
        assertThat(saved.depth).isEqualTo(OverlayDepth.IN_SLAB)
    }

    @Test
    fun nothingToDrawOrNoWallIsRefused() = runTest {
        assertThat(repo.addOnWall("home", "w-north", supply.copy(points = emptyList()))).isEqualTo(OverlaySave.Refused(OverlayProblem.NO_POINTS))
        assertThat(repo.addOnWall("home", "w-gone", supply)).isEqualTo(OverlaySave.Refused(OverlayProblem.WALL_GONE))
        assertThat(repo.update("missing", supply)).isEqualTo(OverlaySave.Refused(OverlayProblem.NOT_FOUND))
        assertThat(changes).isEmpty()
    }

    @Test
    fun twoTapsOnOneSpotMakeAPoint() = runTest {
        val outlet = OverlayDraft(OverlayKind.OUTLET, listOf(OverlayPoint(0.5f, 0.8f), OverlayPoint(0.5f, 0.8f)), WallStage.FINISHED)
        repo.addOnWall("home", "w-north", outlet)
        val saved = repo.observeForWall("w-north").first().single()
        assertThat(saved.points).hasSize(1)
        assertThat(saved.shape).isEqualTo(OverlayShape.POINT)
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun pointsDragAndFieldsEditAndDeleteRemovesIt() = runTest {
        val id = (repo.addOnWall("home", "w-north", supply) as OverlaySave.Saved).id

        assertThat(repo.movePoint(id, 1, OverlayPoint(0.45f, 0.9f))).isTrue()
        assertThat(repo.movePoint(id, 5, OverlayPoint(0f, 0f))).isFalse()
        assertThat(repo.get(id)?.points?.get(1)).isEqualTo(OverlayPoint(0.45f, 0.9f))

        val edited = supply.copy(kind = OverlayKind.WATER_HOT, label = "Dishwasher supply", depth = OverlayDepth.IN_CEILING, itemId = "dishwasher",
            points = repo.get(id)?.points.orEmpty())
        assertThat(repo.update(id, edited)).isEqualTo(OverlaySave.Saved(id))
        val after = repo.get(id)
        assertThat(after?.kind).isEqualTo(OverlayKind.WATER_HOT)
        assertThat(after?.label).isEqualTo("Dishwasher supply")
        assertThat(after?.itemId).isEqualTo("dishwasher")
        assertThat(after?.wallId).isEqualTo("w-north")

        assertThat(repo.delete(id)).isTrue()
        assertThat(repo.get(id)).isNull()
        assertThat(repo.observeForWall("w-north").first()).isEmpty()
        assertThat(repo.delete(id)).isFalse()
        assertThat(changes).containsExactly("overlay/$id", "overlay/$id", "overlay/$id", "overlay/$id").inOrder()
    }
}

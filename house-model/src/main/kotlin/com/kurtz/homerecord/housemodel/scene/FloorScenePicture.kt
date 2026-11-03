package com.kurtz.homerecord.housemodel.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.kurtz.homerecord.housemodel.blueprint.RoomPalette
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlin.math.max
import kotlin.math.min

/**
 * The 3D floor as a still picture from the starting camera, flat-shaded in the same colors as the live view (walls with
 * a photo at [stage] in a photo tone, floors below the chosen one faded). Used where Filament cannot draw: previews, screenshot tests and the screen catalog.
 * [markers] (AC-C23) are drawn over the house in their layer colors, the same width as in the live view; those on a wall
 * face turned away from the camera are left out, as the live view hides them behind the wall.
 */
@Composable
fun FloorScenePicture(scene: FloorScene, stage: WallStage, modifier: Modifier = Modifier, markers: List<SceneMarker> = emptyList()) {
    val triangles = remember(scene.mesh) { SceneProjection.project(scene.mesh) }
    val pictured = remember(scene, stage) { scene.picturesAt(stage).keys }
    val projected = remember(markers) {
        markers.filter { it.points.isNotEmpty() && SceneProjection.faces(it.facing) }.map { m -> m to m.points.map { SceneProjection.projectPoint(it) } }
    }
    Canvas(modifier) {
        if (triangles.isEmpty()) return@Canvas
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        triangles.forEach { t ->
            for (i in 0..2) {
                minX = min(minX, t.xs[i]); maxX = max(maxX, t.xs[i])
                minY = min(minY, t.ys[i]); maxY = max(maxY, t.ys[i])
            }
        }
        val pad = 0.1f
        val scale = min(size.width * (1 - 2 * pad) / max(maxX - minX, 0.01f), size.height * (1 - 2 * pad) / max(maxY - minY, 0.01f))
        val ox = (size.width - (maxX - minX) * scale) / 2 - minX * scale
        val oy = (size.height - (maxY - minY) * scale) / 2 - minY * scale
        val path = Path()
        triangles.forEach { t ->
            val key = t.key
            val ghost = scene.isGhost(key)
            if (ghost && key is MeshPartKey.Glass) return@forEach
            val base = if (ghost) RoomPalette.ghost else when (key) {
                is MeshPartKey.Floor -> RoomPalette.floor
                is MeshPartKey.Glass -> RoomPalette.glass
                is MeshPartKey.WallFace -> if (key in pictured) RoomPalette.photo else RoomPalette.tint(scene.tintOf(key.roomId))
                is MeshPartKey.Shell -> RoomPalette.tint(scene.tintOf(key.roomId))
            }
            val color = if (key is MeshPartKey.Glass || ghost) base else lerp(Color.Black, base, t.light).copy(alpha = base.alpha)
            path.reset()
            path.moveTo(ox + t.xs[0] * scale, oy + t.ys[0] * scale)
            path.lineTo(ox + t.xs[1] * scale, oy + t.ys[1] * scale)
            path.lineTo(ox + t.xs[2] * scale, oy + t.ys[2] * scale)
            path.close()
            drawPath(path, color)
            // A hairline in the same color closes the anti-aliased seams between neighbouring triangles.
            if (key !is MeshPartKey.Glass && !ghost) drawPath(path, color, style = Stroke(width = 1f))
        }
        projected.forEach { (marker, pts) ->
            val color = MarkerColors.of(marker)
            val at = pts.map { (x, y) -> Offset(ox + x * scale, oy + y * scale) }
            if (marker.isRun) {
                val width = max(MarkerMeshBuilder.RUN_WIDTH_M * scale, MIN_STROKE_PX)
                path.reset()
                path.moveTo(at[0].x, at[0].y)
                at.drop(1).forEach { path.lineTo(it.x, it.y) }
                drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                val r = if (marker.target is MarkerTarget.ItemMark) MarkerMeshBuilder.ITEM_RADIUS_M else MarkerMeshBuilder.SPOT_RADIUS_M
                drawCircle(color, radius = max(r * scale, MIN_STROKE_PX), center = at[0])
            }
        }
    }
}

private const val MIN_STROKE_PX = 3f

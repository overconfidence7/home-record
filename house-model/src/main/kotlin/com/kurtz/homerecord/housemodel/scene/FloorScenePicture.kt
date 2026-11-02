package com.kurtz.homerecord.housemodel.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.kurtz.homerecord.housemodel.blueprint.RoomPalette
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlin.math.max
import kotlin.math.min

/**
 * The 3D floor as a still picture from the starting camera, flat-shaded in the same colors as the live view (walls with
 * a photo at [stage] in a photo tone). Used where Filament cannot draw: previews, screenshot tests and the screen catalog.
 */
@Composable
fun FloorScenePicture(scene: FloorScene, stage: WallStage, modifier: Modifier = Modifier) {
    val triangles = remember(scene.mesh) { SceneProjection.project(scene.mesh) }
    val pictured = remember(scene, stage) { scene.picturesAt(stage).keys }
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
            val base = when (key) {
                is MeshPartKey.Floor -> RoomPalette.floor
                is MeshPartKey.Glass -> RoomPalette.glass
                is MeshPartKey.WallFace -> if (key in pictured) RoomPalette.photo else RoomPalette.tint(scene.tintOf(key.roomId))
                is MeshPartKey.Shell -> RoomPalette.tint(scene.tintOf(key.roomId))
            }
            val color = if (key is MeshPartKey.Glass) base else lerp(Color.Black, base, t.light).copy(alpha = base.alpha)
            path.reset()
            path.moveTo(ox + t.xs[0] * scale, oy + t.ys[0] * scale)
            path.lineTo(ox + t.xs[1] * scale, oy + t.ys[1] * scale)
            path.lineTo(ox + t.xs[2] * scale, oy + t.ys[2] * scale)
            path.close()
            drawPath(path, color)
            // A hairline in the same color closes the anti-aliased seams between neighbouring triangles.
            if (key !is MeshPartKey.Glass) drawPath(path, color, style = Stroke(width = 1f))
        }
    }
}

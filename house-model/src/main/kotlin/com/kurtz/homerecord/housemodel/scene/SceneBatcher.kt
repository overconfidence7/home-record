package com.kurtz.homerecord.housemodel.scene

import com.kurtz.homerecord.housemodel.walls.WallStage

/** The look a batch of triangles is drawn with; one material instance per distinct value. */
sealed interface DrawMaterial {
    /** A room's color ([RoomTint] index): shells and walls without a picture. */
    data class Tint(val index: Int) : DrawMaterial

    /** A wall picture; drawn in [fallback] until its texture has loaded. */
    data class Picture(val path: String, val fallback: Int) : DrawMaterial

    data object Floor : DrawMaterial
    data object Glass : DrawMaterial

    /** A room on a floor below the chosen one: faint see-through grey. */
    data object Ghost : DrawMaterial
}

/** One renderable: every triangle of one room that shares one [material], merged into a single [mesh]. */
data class DrawBatch(val roomId: String, val material: DrawMaterial, val mesh: MeshPart) {
    val bounds: MeshBounds get() = mesh.bounds()
}

/**
 * Merges a scene's parts into as few renderables as the materials allow (AC-C24): one per room and material, so a
 * three-floor home draws in about three calls per room plus one per photographed wall instead of one per wall face, and
 * Filament can still cull the rooms outside the camera's view one by one (each batch keeps its own bounding box).
 * Ghost rooms lose their glass; a wall with a picture at [stage] gets its own batch so its texture can be swapped in when
 * it loads. Pure: runs off the main thread.
 */
object SceneBatcher {
    fun batch(scene: FloorScene, stage: WallStage): List<DrawBatch> {
        val pictures = scene.picturesAt(stage)
        val groups = LinkedHashMap<Pair<String, DrawMaterial>, MutableList<MeshPart>>()
        for (part in scene.mesh.parts) {
            val key = part.key
            val ghost = scene.isGhost(key)
            if (ghost && key is MeshPartKey.Glass) continue
            val tint = scene.tintOf(key.roomId)
            val material = if (ghost) DrawMaterial.Ghost else when (key) {
                is MeshPartKey.WallFace -> pictures[key]?.let { DrawMaterial.Picture(it, tint) } ?: DrawMaterial.Tint(tint)
                is MeshPartKey.Shell -> DrawMaterial.Tint(tint)
                is MeshPartKey.Floor -> DrawMaterial.Floor
                is MeshPartKey.Glass -> DrawMaterial.Glass
            }
            groups.getOrPut(key.roomId to material) { ArrayList() } += part
        }
        return groups.map { (k, parts) -> DrawBatch(k.first, k.second, merge(parts)) }.filter { it.mesh.vertexCount > 0 }
    }

    /** The parts' triangles in one buffer; the merged part keeps the first part's key. */
    fun merge(parts: List<MeshPart>): MeshPart {
        if (parts.size == 1) return parts[0]
        val vertexTotal = parts.sumOf { it.vertexCount }
        val positions = FloatArray(vertexTotal * 3)
        val normals = FloatArray(vertexTotal * 3)
        val uvs = FloatArray(vertexTotal * 2)
        val indices = IntArray(parts.sumOf { it.indices.size })
        var v = 0
        var n = 0
        for (part in parts) {
            part.positions.copyInto(positions, v * 3)
            part.normals.copyInto(normals, v * 3)
            part.uvs.copyInto(uvs, v * 2)
            for (index in part.indices) indices[n++] = index + v
            v += part.vertexCount
        }
        return MeshPart(parts[0].key, positions, normals, uvs, indices)
    }
}

/**
 * How large wall pictures are decoded for the GPU (AC-C24): the long side halves until every picture the scene may show
 * fits the texture budget together (RGBA, a third more for mipmaps), never above [MAX_PX] or below [MIN_PX]. Twelve
 * photographed walls fit at 1024 px; a home with 20 to 46 fits at 512.
 */
object TextureBudget {
    const val MAX_PX = 1024
    const val MIN_PX = 256
    const val BUDGET_BYTES = 64L * 1024 * 1024

    fun bytesPerTexture(sidePx: Int): Long = sidePx.toLong() * sidePx * 4 * 4 / 3

    fun maxSidePx(textureCount: Int): Int {
        var side = MAX_PX
        while (side > MIN_PX && textureCount * bytesPerTexture(side) > BUDGET_BYTES) side /= 2
        return side
    }

    /** The power-of-two sample size that brings a [width] × [height] picture down to [maxSidePx] on its long side. */
    fun sampleSize(width: Int, height: Int, maxSidePx: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxSidePx) sample *= 2
        return sample
    }
}

/**
 * Frame timing for the 3D view's measurement line (AC-C24): the time from the view opening to its first frame and the
 * frame rate over a rolling [windowNanos]. QA reads the line from logcat (`adb logcat -s HomeRecord3D`). Not thread-safe;
 * fed from the render callback on the main thread.
 */
class FrameStats(private val startNanos: Long, private val windowNanos: Long = 2_000_000_000L) {
    private val times = ArrayDeque<Long>()
    var firstFrameMs: Long? = null
        private set
    var frames: Long = 0
        private set

    fun onFrame(nanos: Long) {
        if (firstFrameMs == null) firstFrameMs = (nanos - startNanos) / 1_000_000
        frames++
        times.addLast(nanos)
        while (times.size > 1 && nanos - times.first() > windowNanos) times.removeFirst()
    }

    /** Frames per second over the window, or null before two frames. */
    val fps: Float?
        get() {
            if (times.size < 2) return null
            val span = times.last() - times.first()
            return if (span <= 0) null else (times.size - 1) * 1_000_000_000f / span
        }

    /** The logcat line: first render, frame rate, and what the view draws. */
    fun summary(draws: Int, textures: Int, texturePx: Int): String =
        "first frame ${firstFrameMs ?: "-"} ms; ${fps?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: "-"} fps; " +
            "$frames frames; $draws draws; $textures textures at $texturePx px"
}

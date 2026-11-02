package com.kurtz.homerecord.housemodel.scene

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Texture
import com.kurtz.homerecord.housemodel.blueprint.RoomPalette
import com.kurtz.homerecord.housemodel.walls.WallStage
import dev.romainguy.kotlin.math.Float2
import io.github.sceneview.SceneView
import io.github.sceneview.createDefaultCameraManipulator
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.safeDestroyGeometry
import io.github.sceneview.safeDestroyTexture
import io.github.sceneview.texture.ImageTexture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The live 3D floor (AC-C20) on SceneView/Filament: one renderable per [MeshPart], wall faces textured with their
 * picture at [stage] (loaded off the main thread, downscaled), other faces lit in the room's tint, a neutral floor and
 * translucent glass. One finger orbits, two pan, pinch zooms (SceneView's camera manipulator); a new [resetCount] puts
 * the camera back where it started: south of the floor, turned and looking down as in [SceneProjection].
 */
@Composable
fun FloorSceneView(scene: FloorScene, stage: WallStage, resetCount: Int, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    // Declared before SceneView so they are released after its nodes (Compose forgets in reverse order).
    val resources = remember(engine, materialLoader) { SceneResources(engine, materialLoader) }
    DisposableEffect(resources) { onDispose { resources.destroy() } }
    val geometries = remember(engine, scene.mesh) { scene.mesh.parts.map { it.key to it.toGeometry(engine) } }
    DisposableEffect(geometries) { onDispose { geometries.forEach { (_, g) -> engine.safeDestroyGeometry(g) } } }

    val pictures = remember(scene, stage) { scene.picturesAt(stage) }
    val bitmaps by produceState(emptyMap<String, Bitmap>(), pictures) {
        value = withContext(Dispatchers.IO) {
            pictures.values.distinct().filterNot(resources::hasTexture).mapNotNull { path -> decode(path)?.let { path to it } }.toMap()
        }
    }
    LaunchedEffect(bitmaps) { bitmaps.forEach { (path, bitmap) -> resources.addTexture(path, bitmap) } }

    val bounds = scene.mesh.bounds
    val manipulator = remember(bounds, resetCount) {
        val target = Position(bounds.centerX, bounds.minY, bounds.centerZ)
        val distance = max(bounds.radius, MIN_RADIUS_M) * DISTANCE_PER_RADIUS
        val yaw = Math.toRadians(SceneProjection.START_YAW_DEG.toDouble())
        val pitch = Math.toRadians(SceneProjection.START_PITCH_DEG.toDouble())
        val eye = Position(
            target.x + (distance * sin(yaw) * cos(pitch)).toFloat(),
            target.y + (distance * sin(pitch)).toFloat(),
            target.z + (distance * cos(yaw) * cos(pitch)).toFloat(),
        )
        createDefaultCameraManipulator(eyePosition = eye, targetPosition = target)
    }

    SceneView(
        modifier = modifier,
        engine = engine,
        materialLoader = materialLoader,
        cameraManipulator = manipulator,
        autoCenterContent = false,
    ) {
        geometries.forEach { (key, geometry) ->
            val material = when (key) {
                is MeshPartKey.WallFace -> pictures[key]?.let { resources.textured(it) } ?: resources.tint(scene.tintOf(key.roomId))
                is MeshPartKey.Shell -> resources.tint(scene.tintOf(key.roomId))
                is MeshPartKey.Floor -> resources.floor
                is MeshPartKey.Glass -> resources.glass
            }
            MeshNode(
                primitiveType = RenderableManager.PrimitiveType.TRIANGLES,
                vertexBuffer = geometry.vertexBuffer,
                indexBuffer = geometry.indexBuffer,
                boundingBox = geometry.boundingBox,
                materialInstance = material,
            )
        }
    }
}

/** Material instances and textures for one engine, made on first use and released together. Main thread only. */
private class SceneResources(private val engine: Engine, private val loader: MaterialLoader) {
    private val tints = HashMap<Int, MaterialInstance>()
    /** Snapshot state, so a texture arriving recomposes the nodes that can now show it. */
    private val textures = mutableStateMapOf<String, Pair<Texture, MaterialInstance>>()
    private var destroyed = false

    val floor: MaterialInstance by lazy { loader.createColorInstance(RoomPalette.floor, roughness = 0.9f) }
    val glass: MaterialInstance by lazy { loader.createColorInstance(RoomPalette.glass, roughness = 0.1f) }

    fun tint(index: Int): MaterialInstance = tints.getOrPut(index) { loader.createColorInstance(RoomPalette.tint(index), roughness = 0.8f) }

    fun hasTexture(path: String) = path in textures

    fun textured(path: String): MaterialInstance? = textures[path]?.second

    fun addTexture(path: String, bitmap: Bitmap) {
        if (destroyed || path in textures) return
        try {
            val texture = ImageTexture.Builder().bitmap(bitmap).build(engine)
            textures[path] = texture to loader.createTextureInstance(texture, roughness = 0.8f)
        } catch (e: Exception) {
            // A picture the GPU refuses leaves the wall in its tint.
            Log.w(TAG, "Wall texture not loaded", e)
        }
    }

    fun destroy() {
        destroyed = true
        textures.values.forEach { (texture, material) ->
            loader.destroyMaterialInstance(material)
            engine.safeDestroyTexture(texture)
        }
        textures.clear()
        tints.values.forEach(loader::destroyMaterialInstance)
        tints.clear()
    }
}

private fun MeshPart.toGeometry(engine: Engine): Geometry {
    val vertices = List(vertexCount) { i ->
        Geometry.Vertex(
            position = Position(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]),
            normal = Direction(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]),
            uvCoordinate = Float2(uvs[i * 2], uvs[i * 2 + 1]),
        )
    }
    return Geometry.Builder().vertices(vertices).indices(indices.toList()).build(engine)
}

/** Decodes a wall picture no larger than [MAX_TEXTURE_PX] on its long side, or null when it cannot be read. */
private fun decode(path: String): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_TEXTURE_PX) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (e: Exception) {
    Log.w(TAG, "Wall picture unreadable", e)
    null
}

private const val TAG = "FloorScene"
private const val MAX_TEXTURE_PX = 1024
private const val MIN_RADIUS_M = 2f
private const val DISTANCE_PER_RADIUS = 2.2f

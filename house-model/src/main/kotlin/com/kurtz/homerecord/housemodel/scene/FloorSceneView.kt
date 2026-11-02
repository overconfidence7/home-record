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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The live 3D floor (AC-C20, AC-C21) on SceneView/Filament. The scene is merged off the main thread into one renderable
 * per room and material ([SceneBatcher], AC-C24), so Filament culls room by room and a three-floor home stays near three
 * draws per room; wall faces with a picture at [stage] are textured (decoded off the main thread at the size
 * [TextureBudget] allows for every picture the scene holds, one texture per file) and keep the room's tint until their
 * texture arrives; other faces are lit in the room's tint, floors neutral, glass translucent, floors below the chosen one
 * a faint see-through grey. One finger orbits, two pan, pinch zooms (SceneView's camera manipulator); a new
 * [resetCount] puts the camera back where it started: south of the floor, turned and looking down as in
 * [SceneProjection]. Every two seconds while frames are drawn it logs [FrameStats] under [PERF_TAG], timed from
 * [openedAtNanos] (the screen opening), for QA's AC-C24 measurement.
 */
@Composable
fun FloorSceneView(
    scene: FloorScene,
    stage: WallStage,
    resetCount: Int,
    modifier: Modifier = Modifier,
    openedAtNanos: Long = remember { System.nanoTime() },
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    // Declared before SceneView so they are released after its nodes (Compose forgets in reverse order).
    val resources = remember(engine, materialLoader) { SceneResources(engine, materialLoader) }
    DisposableEffect(resources) { onDispose { resources.destroy() } }
    val batches by produceState(emptyList<DrawBatch>(), scene, stage) {
        value = withContext(Dispatchers.Default) { SceneBatcher.batch(scene, stage) }
    }
    val geometries = remember(engine, batches) { batches.map { it to it.mesh.toGeometry(engine) } }
    DisposableEffect(geometries) { onDispose { geometries.forEach { (_, g) -> engine.safeDestroyGeometry(g) } } }

    // Sized for every picture the scene may show at any stage, so switching stages never needs a bigger budget.
    val texturePx = remember(scene) { TextureBudget.maxSidePx(scene.pictures.values.flatMap { it.values }.distinct().size) }
    val pictures = remember(batches) { batches.mapNotNull { (it.material as? DrawMaterial.Picture)?.path }.distinct() }
    val bitmaps by produceState(emptyMap<String, Bitmap>(), pictures, texturePx) {
        value = withContext(Dispatchers.IO) {
            pictures.filterNot(resources::hasTexture).mapNotNull { path -> decode(path, texturePx)?.let { path to it } }.toMap()
        }
    }
    LaunchedEffect(bitmaps) { bitmaps.forEach { (path, bitmap) -> resources.addTexture(path, bitmap) } }

    val stats = remember(openedAtNanos) { FrameStats(openedAtNanos) }
    val draws by rememberUpdatedState(geometries.size)
    LaunchedEffect(stats) {
        var logged = -1L
        while (true) {
            delay(PERF_LOG_MS)
            if (stats.frames != logged) {
                logged = stats.frames
                Log.i(PERF_TAG, stats.summary(draws = draws, textures = resources.textureCount, texturePx = texturePx))
            }
        }
    }

    val bounds = scene.focus
    val lookAtY = scene.lookAtY
    val manipulator = remember(bounds, lookAtY, resetCount) {
        val target = Position(bounds.centerX, lookAtY, bounds.centerZ)
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
        // Timed once the scene is on screen: the first frame counted is the first with the house in it.
        onFrame = { nanos -> if (draws > 0) stats.onFrame(nanos) },
    ) {
        geometries.forEach { (batch, geometry) ->
            val material = when (val m = batch.material) {
                is DrawMaterial.Picture -> resources.textured(m.path) ?: resources.tint(m.fallback)
                is DrawMaterial.Tint -> resources.tint(m.index)
                DrawMaterial.Floor -> resources.floor
                DrawMaterial.Glass -> resources.glass
                DrawMaterial.Ghost -> resources.ghost
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
    val ghost: MaterialInstance by lazy { loader.createColorInstance(RoomPalette.ghost, roughness = 1f) }

    fun tint(index: Int): MaterialInstance = tints.getOrPut(index) { loader.createColorInstance(RoomPalette.tint(index), roughness = 0.8f) }

    fun hasTexture(path: String) = path in textures

    val textureCount: Int get() = textures.size

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

/** Decodes a wall picture no larger than [maxSidePx] on its long side, or null when it cannot be read. */
private fun decode(path: String, maxSidePx: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sample = TextureBudget.sampleSize(bounds.outWidth, bounds.outHeight, maxSidePx)
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (e: Exception) {
    Log.w(TAG, "Wall picture unreadable", e)
    null
}

private const val TAG = "FloorScene"
/** Logcat tag of the AC-C24 measurement line: `adb logcat -s HomeRecord3D`. */
const val PERF_TAG = "HomeRecord3D"
private const val PERF_LOG_MS = 2_000L
private const val MIN_RADIUS_M = 2f
private const val DISTANCE_PER_RADIUS = 2.2f

package com.kurtz.homerecord.housemodel.scene

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** One triangle of the static picture, in picture units (x right, y down), with its part and how lit it is (0..1). */
class ProjectedTriangle(val key: MeshPartKey, val xs: FloatArray, val ys: FloatArray, val depth: Float, val light: Float)

/**
 * A still of the 3D floor for previews, screenshots and the screen catalog, where Filament cannot run: the mesh seen
 * from the 3D view's starting camera (from the south, turned [yawDeg] and looking down [pitchDeg]) with a parallel
 * projection, faces turned away dropped (glass is two-sided and always kept) and the rest ordered far to near so a
 * painter's draw hides what is behind.
 */
object SceneProjection {
    const val START_YAW_DEG = -30f
    const val START_PITCH_DEG = 40f

    fun project(mesh: PlanMesh, yawDeg: Float = START_YAW_DEG, pitchDeg: Float = START_PITCH_DEG): List<ProjectedTriangle> {
        val yaw = Math.toRadians(yawDeg.toDouble())
        val pitch = Math.toRadians(pitchDeg.toDouble())
        val cy = cos(yaw).toFloat()
        val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat()
        val sp = sin(pitch).toFloat()
        // Camera basis: right, up and toward-viewer, from a camera south of the house (+z) raised by pitch.
        val right = floatArrayOf(cy, 0f, -sy)
        val toViewer = floatArrayOf(sy * cp, sp, cy * cp)
        val up = cross(toViewer, right)
        val sun = normalize(floatArrayOf(0.4f, 1f, 0.6f))
        val out = ArrayList<ProjectedTriangle>()
        for (part in mesh.parts) {
            val p = part.positions
            val n = part.normals
            val idx = part.indices
            for (t in 0 until idx.size / 3) {
                val a = idx[t * 3]
                val b = idx[t * 3 + 1]
                val c = idx[t * 3 + 2]
                val nx = n[a * 3] + n[b * 3] + n[c * 3]
                val ny = n[a * 3 + 1] + n[b * 3 + 1] + n[c * 3 + 1]
                val nz = n[a * 3 + 2] + n[b * 3 + 2] + n[c * 3 + 2]
                val normal = normalize(floatArrayOf(nx, ny, nz))
                val facing = dot(normal, toViewer)
                val glass = part.key is MeshPartKey.Glass
                if (facing <= 0f && !glass) continue
                val xs = FloatArray(3)
                val ys = FloatArray(3)
                var depth = 0f
                intArrayOf(a, b, c).forEachIndexed { i, v ->
                    val vx = p[v * 3]
                    val vy = p[v * 3 + 1]
                    val vz = p[v * 3 + 2]
                    xs[i] = vx * right[0] + vy * right[1] + vz * right[2]
                    ys[i] = -(vx * up[0] + vy * up[1] + vz * up[2])
                    depth += vx * toViewer[0] + vy * toViewer[1] + vz * toViewer[2]
                }
                val light = 0.55f + 0.45f * max(0f, kotlin.math.abs(dot(normal, sun)))
                out += ProjectedTriangle(part.key, xs, ys, depth / 3f, light.coerceAtMost(1f))
            }
        }
        // Far first; floors under walls at equal depth.
        out.sortWith(compareBy<ProjectedTriangle>({ it.depth }, { if (it.key is MeshPartKey.Floor) 0 else 1 }))
        return out
    }

    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun cross(a: FloatArray, b: FloatArray) =
        floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private fun normalize(v: FloatArray): FloatArray {
        val l = sqrt(dot(v, v))
        return if (l == 0f) v else floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }
}

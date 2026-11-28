package com.kurtz.homerecord.housemodel.scene

/**
 * Turns a mesh's wall-space texture coordinates into the ones handed to Filament ([judge:hm-3d-wall-photo-mirrored]).
 *
 * The mesh keeps v measured down from the top of the wall ([PlanMeshBuilder], [SceneMarkers] rely on it). SceneView's
 * materials are compiled with matc's default `flipUV`, so the shader samples at `1 - v`, and Filament reads row 0 of
 * an uploaded Android bitmap (its top row) at a sampled v of 0. Handing the mesh's v over as is therefore painted the
 * photo upside down (the QA fixture's "KITCHEN NORTH WALL" read with T, N, R and L upside down). Flipping v here puts the
 * photo's top row on the top of the wall; u is unchanged (left of the photo on the left of the wall seen from inside).
 */
object TextureUv {
    /** The (u, v) to give Filament's vertex buffer for mesh coordinates [u] and [vDown] (0 at the top of the wall). */
    fun forFilament(u: Float, vDown: Float): Pair<Float, Float> = u to (1f - vDown)

    /** The v the material samples with for vertex-buffer [v] (matc `flipUV`). */
    fun sampled(v: Float): Float = 1f - v

    /** The bitmap row fraction (0 = top row) Filament reads at sampled v [vSampled]: row 0 is uploaded first. */
    fun bitmapRow(vSampled: Float): Float = vSampled
}

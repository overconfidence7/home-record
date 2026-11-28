package com.kurtz.homerecord.housemodel.scene

import kotlin.math.atan
import kotlin.math.sin
import kotlin.math.tan

/**
 * How far the 3D view's camera stands back and how wide its lens is ([judge:3d-default-framing]).
 *
 * The floor view must open with the whole floor on screen with a margin, on a tall phone view as well as a wide one, so
 * the distance fits the floor's bounding sphere inside the narrower of the two fields of view. Filament derives the
 * vertical field of view from the focal length against a 24 mm sensor height; the horizontal one follows from the
 * view's aspect. The room view uses a wider lens so it takes in the corner and the floor, like a phone's wide camera.
 * Pure: tested on the JVM.
 */
object SceneFraming {
    const val FLOOR_FOCAL_MM = 28.0
    const val ROOM_FOCAL_MM = 18.0
    const val SENSOR_HEIGHT_MM = 24.0
    const val MARGIN = 0.15f

    /** Half the vertical field of view in radians for a [focalMm] lens. */
    fun halfVerticalFov(focalMm: Double): Double = atan(SENSOR_HEIGHT_MM / (2 * focalMm))

    /** Half the horizontal field of view in radians for a [focalMm] lens on a view [aspect] (width / height) wide. */
    fun halfHorizontalFov(focalMm: Double, aspect: Float): Double = atan(tan(halfVerticalFov(focalMm)) * aspect)

    /**
     * The distance from the target at which a sphere of [radius] meters fits inside both fields of view with [margin]
     * to spare; never under [minRadius] meters' worth, so a closet is not filled edge to edge.
     */
    fun fitDistance(radius: Float, aspect: Float, focalMm: Double = FLOOR_FOCAL_MM, minRadius: Float = 2f, margin: Float = MARGIN): Float {
        val a = if (aspect > 0f && aspect.isFinite()) aspect else 1f
        val half = minOf(halfVerticalFov(focalMm), halfHorizontalFov(focalMm, a))
        return (maxOf(radius, minRadius) * (1 + margin) / sin(half)).toFloat()
    }
}

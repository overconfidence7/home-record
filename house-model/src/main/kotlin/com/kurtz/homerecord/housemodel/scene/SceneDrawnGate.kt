package com.kurtz.homerecord.housemodel.scene

/**
 * When `<app>_3d_scene_drawn` may appear ([judge:hm-3d-room-blank-recurs]): a single good [SceneFrameCheck] is not
 * enough, since a room view caught for one copy mid-way through drawing and then washed out passed H09/H11 on a white
 * view. The tag waits for [CONFIRMATIONS] good checks in a row; any blank check starts the count again. Pure, so it is
 * unit tested; [FloorSceneView] keeps one per scene and camera.
 */
class SceneDrawnGate {
    private var good = 0

    /** Records one check; true once the scene counts as drawn. */
    fun onCheck(onScreen: Boolean): Boolean {
        good = if (onScreen) good + 1 else 0
        return good >= CONFIRMATIONS
    }

    fun reset() {
        good = 0
    }

    companion object {
        const val CONFIRMATIONS = 2
    }
}

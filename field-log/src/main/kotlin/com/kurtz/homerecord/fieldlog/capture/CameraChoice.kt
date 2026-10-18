package com.kurtz.homerecord.fieldlog.capture

/** Picks which lens to bind: back when present, else front, else none (e.g. emulators with one camera). */
object CameraChoice {
    enum class Lens { BACK, FRONT, NONE }

    fun pick(hasBack: Boolean, hasFront: Boolean): Lens = when {
        hasBack -> Lens.BACK
        hasFront -> Lens.FRONT
        else -> Lens.NONE
    }
}

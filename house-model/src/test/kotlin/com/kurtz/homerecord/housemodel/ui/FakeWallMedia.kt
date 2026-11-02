package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.media.CaptureTarget
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.walls.TextureSize
import com.kurtz.homerecord.housemodel.walls.WallOutline
import com.kurtz.homerecord.housemodel.walls.WallTextureMaker

/** In-memory [MediaFiles]: capture targets are numbered, a camera "writes" only when [written] holds the path. */
class FakeWallMedia : MediaFiles {
    val written = mutableSetOf<String>()
    val discarded = mutableListOf<String>()
    var importResult: String? = "media/picked.jpg"
    private var n = 0

    override fun newCaptureTarget(): CaptureTarget {
        n++
        return CaptureTarget("content://test/media/$n.jpg", "media/$n.jpg")
    }

    override suspend fun import(uri: String): String? = importResult

    override fun exists(path: String) = path in written

    override fun discard(path: String) {
        discarded += path
        written -= path
    }
}

/** [WallTextureMaker] that records what it was asked to straighten. */
class FakeTextureMaker(var size: TextureSize? = TextureSize(4032, 3024), var fails: Boolean = false) : WallTextureMaker {
    val made = mutableListOf<Triple<String, WallOutline, Pair<Int, Int>>>()

    override suspend fun photoSize(path: String): TextureSize? = size

    override suspend fun make(path: String, outline: WallOutline, lengthIn: Int, heightIn: Int): String? {
        if (fails) return null
        made += Triple(path, outline, lengthIn to heightIn)
        return path.removeSuffix(".jpg") + "-wall.jpg"
    }
}

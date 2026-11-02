package com.kurtz.homerecord.housemodel.walls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads wall photos and writes their straightened textures off the main thread; faked in tests. */
interface WallTextureMaker {
    /** The photo's upright size, or null when the file is not a readable image. */
    suspend fun photoSize(path: String): TextureSize?

    /** Straightens the outlined wall in [path] to the wall's [lengthIn] × [heightIn]; the texture's path, or null on failure. */
    suspend fun make(path: String, outline: WallOutline, lengthIn: Int, heightIn: Int): String?
}

/** [WallTextureMaker] on [WallTextureBuilder]; the texture sits beside the photo as `<name>-wall.jpg`. */
class AndroidWallTextureMaker : WallTextureMaker {
    override suspend fun photoSize(path: String): TextureSize? = withContext(Dispatchers.IO) { WallTextureBuilder.uprightSize(path) }

    override suspend fun make(path: String, outline: WallOutline, lengthIn: Int, heightIn: Int): String? = withContext(Dispatchers.Default) {
        WallTextureBuilder.build(path, outline, lengthIn, heightIn, texturePathFor(path))?.path
    }

    companion object {
        fun texturePathFor(photoPath: String): String = photoPath.substringBeforeLast('.', photoPath) + "-wall.jpg"
    }
}

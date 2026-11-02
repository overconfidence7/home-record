package com.kurtz.homerecord.core.media

/**
 * How a wall photo's two files are named in R2 (T048d): `<tenant>/wall_photo/<id>/original.<ext>` for the photo as
 * taken and `<tenant>/wall_photo/<id>/texture.jpg` for the straightened copy the room page and the 3D view show.
 * The fixed names keep the upload queue idempotent per file and tell [UploadedKeys] which key column to fill.
 */
object WallPhotoFiles {
    const val ENTITY = "wall_photo"
    const val TEXTURE = "texture.jpg"
    private const val ORIGINAL = "original"

    /** The R2 file name of the photo as taken, keeping its extension ("original.jpg", "original.png"). */
    fun originalName(path: String): String = "$ORIGINAL.${extension(path)}"

    fun isTexture(filename: String): Boolean = filename == TEXTURE

    /** The image type for a wall photo file, from its extension; JPEG when unknown. */
    fun mime(path: String): String = when (extension(path)) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        else -> "image/jpeg"
    }

    private fun extension(path: String): String =
        path.substringAfterLast('/').substringAfterLast('.', "").lowercase().takeIf { it.matches(EXT) } ?: "jpg"

    private val EXT = Regex("[a-z0-9]{1,5}")
}

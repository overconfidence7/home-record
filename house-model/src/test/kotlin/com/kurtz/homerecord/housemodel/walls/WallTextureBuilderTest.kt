package com.kurtz.homerecord.housemodel.walls

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.ExifInterface
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// AC-C19: WallTextureBuilder straightens the outlined wall with a 4-point Matrix.setPolyToPoly warp into a JPEG of the
// wall's aspect, at most 2048 px on the long side.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallTextureBuilderTest {
    @get:Rule val tmp = TemporaryFolder()

    // A wall photographed from below and to the left: the top edge is shorter and the right side leans in.
    private val trapezoid = floatArrayOf(220f, 120f, 760f, 160f, 900f, 640f, 100f, 600f)

    @Test
    @AcceptanceCriterion("AC-C19")
    fun aSyntheticTrapezoidMapsItsCornersToTheRectangleCornersWithinOnePixel() {
        val size = TextureSize(1200, 600)
        val matrix = WallTextureBuilder.warpMatrix(trapezoid, size)
        assertThat(matrix).isNotNull()
        val mapped = trapezoid.copyOf()
        checkNotNull(matrix).mapPoints(mapped)
        val expected = floatArrayOf(0f, 0f, 1200f, 0f, 1200f, 600f, 0f, 600f)
        for (i in expected.indices) assertThat(mapped[i]).isWithin(1f).of(expected[i])
    }

    @Test
    @AcceptanceCriterion("AC-C19")
    fun aCollapsedOutlineHasNoWarp() {
        val line = floatArrayOf(0f, 0f, 100f, 0f, 200f, 0f, 300f, 0f)
        assertThat(WallTextureBuilder.warpMatrix(line, TextureSize(100, 50))).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C19")
    fun textureTakesTheWallsAspectAndNeverPassesTheCap() {
        // A 14' by 8' wall outlined 900 px across in the photo.
        assertThat(WallTextureBuilder.textureSize(168, 96, 900f, 500f)).isEqualTo(TextureSize(900, 514))
        // A narrow 3' by 8' wall stands upright.
        assertThat(WallTextureBuilder.textureSize(36, 96, 300f, 820f)).isEqualTo(TextureSize(308, 820))
        // A 50 MP shot outlined edge to edge is capped at 2048 on the long side.
        assertThat(WallTextureBuilder.textureSize(240, 96, 7800f, 3500f)).isEqualTo(TextureSize(2048, 819))
        // A wall with no recorded size keeps the outline's own proportions.
        assertThat(WallTextureBuilder.textureSize(0, 0, 800f, 400f)).isEqualTo(TextureSize(800, 400))
    }

    /** An 1000×750 photo, grey all over, with the wall drawn as a red trapezoid and a blue square at its top-left corner. */
    private fun photo(name: String, orientation: Int? = null, width: Int = 1000, height: Int = 750): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.GRAY)
        val paint = Paint().apply { color = Color.RED }
        canvas.drawPath(
            Path().apply {
                moveTo(trapezoid[0], trapezoid[1]); lineTo(trapezoid[2], trapezoid[3]); lineTo(trapezoid[4], trapezoid[5]); lineTo(trapezoid[6], trapezoid[7]); close()
            },
            paint,
        )
        paint.color = Color.BLUE
        canvas.drawRect(trapezoid[0], trapezoid[1], trapezoid[0] + 60f, trapezoid[1] + 60f, paint)
        val file = tmp.newFile(name)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (orientation != null) ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        return file
    }

    private fun outlineFor(w: Float, h: Float) = WallOutline.of(
        (0 until 4).map { PhotoPoint(trapezoid[2 * it] / w, trapezoid[2 * it + 1] / h) },
    ).let(::checkNotNull)

    private fun Int.isRed() = Color.red(this) > 180 && Color.green(this) < 90 && Color.blue(this) < 90
    private fun Int.isBlue() = Color.blue(this) > 180 && Color.red(this) < 90 && Color.green(this) < 90

    @Test
    @AcceptanceCriterion("AC-C19")
    fun buildWritesAStraightenedJpegFilledByTheWall() {
        val source = photo("wall.jpg")
        val dest = File(tmp.root, "media/wall-tex.jpg")

        val texture = WallTextureBuilder.build(source.path, outlineFor(1000f, 750f), lengthIn = 168, heightIn = 96, destPath = dest.path)

        assertThat(texture).isNotNull()
        assertThat(checkNotNull(texture).path).isEqualTo(dest.path)
        val out = BitmapFactory.decodeFile(dest.path)
        assertThat(out.width).isEqualTo(texture.width)
        assertThat(out.height).isEqualTo(texture.height)
        assertThat(out.width.toFloat() / out.height).isWithin(0.01f).of(168f / 96f)
        assertThat(maxOf(out.width, out.height)).isAtMost(WallTextureBuilder.MAX_LONG_SIDE)
        // The wall fills the texture: the inside and the far corners are wall, the outline's top-left lands top-left.
        assertThat(out.getPixel(out.width / 2, out.height / 2).isRed()).isTrue()
        assertThat(out.getPixel(out.width - 4, out.height - 4).isRed()).isTrue()
        assertThat(out.getPixel(4, out.height - 4).isRed()).isTrue()
        assertThat(out.getPixel(6, 6).isBlue()).isTrue()
        assertThat(out.getPixel(out.width - 6, 6).isBlue()).isFalse()
    }

    @Test
    @AcceptanceCriterion("AC-C19")
    fun aPhotoStoredSidewaysIsOutlinedAsShownUpright() {
        // Stored 750×1000 and tagged "turn 90° clockwise", it shows as the 1000×750 picture the outline was drawn on.
        val upright = photo("upright.jpg")
        val stored = File(tmp.root, "stored.jpg")
        val bmp = BitmapFactory.decodeFile(upright.path)
        val turned = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(-90f) }, true)
        stored.outputStream().use { turned.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(stored.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val dest = File(tmp.root, "turned-tex.jpg")

        val texture = WallTextureBuilder.build(stored.path, outlineFor(1000f, 750f), 168, 96, dest.path)

        assertThat(texture).isNotNull()
        val out = BitmapFactory.decodeFile(dest.path)
        assertThat(out.getPixel(out.width / 2, out.height / 2).isRed()).isTrue()
        assertThat(out.getPixel(6, 6).isBlue()).isTrue()
    }

    @Test
    @AcceptanceCriterion("AC-C19")
    fun anUnusableOutlineOrUnreadablePhotoWritesNothing() {
        val source = photo("wall2.jpg")
        val crossed = WallOutline(PhotoPoint(0.1f, 0.1f), PhotoPoint(0.9f, 0.9f), PhotoPoint(0.9f, 0.1f), PhotoPoint(0.1f, 0.9f))
        val dest = File(tmp.root, "none.jpg")
        assertThat(WallTextureBuilder.build(source.path, crossed, 168, 96, dest.path)).isNull()
        assertThat(WallTextureBuilder.build(File(tmp.root, "missing.jpg").path, WallOutline.Initial, 168, 96, dest.path)).isNull()
        assertThat(dest.exists()).isFalse()
    }
}

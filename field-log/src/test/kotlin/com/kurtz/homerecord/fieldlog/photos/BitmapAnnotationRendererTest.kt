package com.kurtz.homerecord.fieldlog.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.photo.Annotation
import com.kurtz.homerecord.core.photo.NormPoint
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// AC-F26: the marked-up copy matches what was drawn, even for a portrait shot stored sideways with an EXIF turn.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapAnnotationRendererTest {
    @get:Rule val tmp = TemporaryFolder()

    /** A 400×200 landscape JPEG, green on the left half and blue on the right, tagged with EXIF [orientation]. */
    private fun fixture(orientation: Int): File {
        val bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.color = Color.GREEN
        canvas.drawRect(0f, 0f, 200f, 200f, paint)
        paint.color = Color.BLUE
        canvas.drawRect(200f, 0f, 400f, 200f, paint)
        val file = tmp.newFile("stored-$orientation.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        return file
    }

    private fun Int.isGreen() = Color.green(this) > 180 && Color.blue(this) < 80 && Color.red(this) < 80
    private fun Int.isBlue() = Color.blue(this) > 180 && Color.green(this) < 80 && Color.red(this) < 80
    // A 3 px red stroke comes back from JPEG blended with its neighbours, so a mark is any clearly red-dominant pixel.
    private fun Int.isMark() = Color.red(this) > 140 && Color.red(this) > Color.green(this) + 40 && Color.red(this) > Color.blue(this) + 60

    @Test
    fun portraitShotTaggedRotate90IsWrittenUprightWithMarksWhereDrawn() = runTest {
        val source = fixture(ExifInterface.ORIENTATION_ROTATE_90)
        assertThat(BitmapAnnotationRenderer.uprightAspect(source.path)).isWithin(0.001f).of(0.5f)
        val dest = File(tmp.root, "marked.jpg")
        // A box in the top-left quarter of the upright picture, as the editor drew it.
        val box = Annotation.Rectangle(NormPoint(0.1f, 0.1f), NormPoint(0.4f, 0.2f))

        assertThat(BitmapAnnotationRenderer.render(source.path, listOf(box), dest.path)).isTrue()

        val out = BitmapFactory.decodeFile(dest.path)
        assertThat(out.width).isEqualTo(200)
        assertThat(out.height).isEqualTo(400)
        // A quarter turn clockwise puts the stored left (green) half on top and the right (blue) half below.
        assertThat(out.getPixel(150, 350).isBlue()).isTrue()
        assertThat(out.getPixel(150, 120).isGreen()).isTrue()
        // The box's left edge sits at 10% of the upright width, between 10% and 20% of its height.
        assertThat(out.getPixel(20, 60).isMark()).isTrue()
        assertThat(out.getPixel(100, 300).isMark()).isFalse()
    }

    @Test
    fun uprightShotKeepsItsSizeAndOneMutableBitmap() {
        val source = fixture(ExifInterface.ORIENTATION_NORMAL)
        val decoded = BitmapAnnotationRenderer.decodeUpright(source.path)
        assertThat(decoded).isNotNull()
        decoded ?: return
        assertThat(decoded.isMutable).isTrue()
        assertThat(decoded.width).isEqualTo(400)
        assertThat(decoded.height).isEqualTo(200)
        assertThat(decoded.getPixel(50, 100).isGreen()).isTrue()
        assertThat(BitmapAnnotationRenderer.uprightAspect(source.path)).isWithin(0.001f).of(2f)
    }

    @Test
    fun rotate180PutsTheRightHalfOnTheLeft() {
        val decoded = BitmapAnnotationRenderer.decodeUpright(fixture(ExifInterface.ORIENTATION_ROTATE_180).path)
        assertThat(decoded).isNotNull()
        decoded ?: return
        assertThat(decoded.isMutable).isTrue()
        assertThat(decoded.width).isEqualTo(400)
        assertThat(decoded.getPixel(50, 100).isBlue()).isTrue()
        assertThat(decoded.getPixel(350, 100).isGreen()).isTrue()
    }

    @Test
    fun unreadableFileWritesNothing() = runTest {
        val bogus = tmp.newFile("not-an-image.jpg").apply { writeText("nope") }
        val dest = File(tmp.root, "out.jpg")
        assertThat(BitmapAnnotationRenderer.render(bogus.path, listOf(Annotation.Circle(NormPoint(0.5f, 0.5f), 0.1f)), dest.path)).isFalse()
        assertThat(dest.exists()).isFalse()
    }
}

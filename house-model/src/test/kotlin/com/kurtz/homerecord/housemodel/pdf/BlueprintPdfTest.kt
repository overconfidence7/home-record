package com.kurtz.homerecord.housemodel.pdf

import org.robolectric.RobolectricTestRunner
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class BlueprintPdfTest {
    private fun rect(id: String, x: Int, y: Int, w: Int, h: Int) =
        PlanRoom(id, id, points = listOf(Pt(x, y), Pt(x + w, y), Pt(x + w, y + h), Pt(x, y + h)))

    // AC-C16
    @Test fun smallHouseGetsTheLargestScaleThatFits() {
        val layout = checkNotNull(BlueprintPageLayout.forRooms(listOf(rect("a", 0, 0, 12 * 12, 10 * 12))))
        assertThat(layout.scale.label).isEqualTo("1/2\" = 1'")
    }

    // AC-C16
    @Test fun bigHouseDropsToASmallerScaleAndStaysInsideTheMargins() {
        val spec = PdfPageSpec()
        val layout = checkNotNull(BlueprintPageLayout.forRooms(listOf(rect("a", 0, 0, 60 * 12, 40 * 12)), spec))
        assertThat(layout.ptPerPlanInch).isLessThan(0.5f * 72f / 12f)
        assertThat(layout.x(0)).isAtLeast(spec.marginPt)
        assertThat(layout.x(60 * 12)).isAtMost(spec.widthPt - spec.marginPt)
        assertThat(layout.y(40 * 12)).isAtMost(spec.heightPt - spec.footerPt - spec.marginPt)
    }

    // AC-C16
    @Test fun scaleBarIsAWholeNumberOfFeetAtTheDrawnScale() {
        val layout = checkNotNull(BlueprintPageLayout.forRooms(listOf(rect("a", 0, 0, 30 * 12, 20 * 12))))
        assertThat(layout.scaleBarFeet).isIn(listOf(1, 2, 5, 10, 20, 50))
        assertThat(layout.scaleBarLengthPt).isWithin(0.01f).of(layout.scaleBarFeet * 12 * layout.ptPerPlanInch)
    }

    // AC-C16
    @Test fun noRoomsMeansNoLayout() {
        assertThat(BlueprintPageLayout.forRooms(emptyList())).isNull()
    }

    // AC-C16 (PdfDocument has no native backing on the JVM, so the page drawing is checked on a bitmap canvas)
    @Test fun aPageDrawsRoomsAndAScaleBarWithoutThrowing() {
        val bitmap = android.graphics.Bitmap.createBitmap(612, 792, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val bar = mutableListOf<Int>()
        val writer = BlueprintPdfWriter({ feet -> bar += feet; "$feet ft" })
        writer.drawPage(canvas, "Maple House", PdfFloor("Main", listOf(rect("Kitchen", 0, 0, 144, 120))))
        writer.drawPage(canvas, "Maple House", PdfFloor("Upper", emptyList()))
        // The scale bar is worded through the resource-backed formatter, once, for the floor with rooms.
        assertThat(bar).hasSize(1)
    }
}

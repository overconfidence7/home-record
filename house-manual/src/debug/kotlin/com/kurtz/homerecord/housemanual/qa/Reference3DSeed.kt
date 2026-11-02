package com.kurtz.homerecord.housemanual.qa

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.walls.WallOutline
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallStage
import java.io.File

/**
 * Debug seed for the 3D performance check (AC-C24, T050b): the regression home grown to the reference size SPEC names,
 * about 3,000 sq ft on three floors (a two-car garage on the main floor and a bonus room upstairs), with Finished
 * photos on 20 walls. The photos are 1600 × 1200 JPEGs drawn here (painted plaster, baseboard, an outlet and a frame),
 * so the 3D view decodes and downsizes them as it would a phone's pictures.
 * `adb shell am broadcast -n com.kurtz.homerecord.housemanual/.qa.SeedReceiver --es scenario 3d-reference`.
 */
object Reference3DSeed {
    private const val FT = 12
    private const val PHOTO_W = 1600
    private const val PHOTO_H = 1200

    val garage: PlanRoom = PlanRoom.rectangle("qa-garage", "Garage", "GARAGE", 32 * FT, 0, 24 * FT, 24 * FT)
    val bonus: PlanRoom = PlanRoom.rectangle("qa-bonus", "Bonus room", "OTHER", 26 * FT, 0, 24 * FT, 23 * FT)

    /** Rooms and how many of their walls (from edge 0) get a Finished photo: 20 in all. */
    val photographed: List<Pair<String, Int>> = listOf(
        "qa-kitchen" to 4, "qa-living" to 4, "qa-dining" to 2, "qa-primary" to 4, "qa-bed2" to 2, "qa-rec" to 2, "qa-garage" to 2,
    )

    val squareFeet: Int
        get() = (RegressionSeed.mainFloor + RegressionSeed.upperFloor + RegressionSeed.basement + garage + bonus)
            .sumOf { r -> (r.points.maxOf { it.x } - r.points.minOf { it.x }) * (r.points.maxOf { it.y } - r.points.minOf { it.y }) } / (FT * FT)

    suspend fun seed(
        db: HomeRecordDatabase,
        repo: HomeRepository,
        wallPhotos: WallPhotoRepository,
        homeId: String,
        mediaDir: File,
    ): Int {
        val floors = db.floorDao().listFor(homeId)
        check(floors.size >= 3) { "regression home has ${floors.size} floors" }
        repo.savePlan(floors[1].id, RegressionSeed.mainFloor + garage)
        repo.savePlan(floors[2].id, RegressionSeed.upperFloor + bonus)
        mediaDir.mkdirs()
        var saved = 0
        photographed.forEachIndexed { r, (roomId, walls) ->
            db.wallDao().listFor(roomId).sortedBy { it.edgeIndex }.take(walls).forEach { wall ->
                val file = File(mediaDir, "qa-3d-${wall.id}.jpg")
                file.outputStream().use { drawWall(PALETTE[(r + wall.edgeIndex) % PALETTE.size], wall.edgeIndex).compress(Bitmap.CompressFormat.JPEG, 85, it) }
                if (wallPhotos.save(wall.id, WallStage.FINISHED, file.absolutePath, file.absolutePath, WallOutline.Initial) != null) saved++
            }
        }
        check(saved == photographed.sumOf { it.second }) { "only $saved wall photos saved" }
        return saved
    }

    private fun drawWall(paint: Int, variant: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(PHOTO_W, PHOTO_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(paint)
        // Plaster texture: faint horizontal streaks so the texture is not one flat color.
        p.color = Color.argb(18, 0, 0, 0)
        for (y in 0 until PHOTO_H step 24) canvas.drawRect(0f, y.toFloat(), PHOTO_W.toFloat(), y + 6f, p)
        p.color = Color.rgb(245, 243, 238)
        canvas.drawRect(0f, PHOTO_H - 90f, PHOTO_W.toFloat(), PHOTO_H.toFloat(), p) // baseboard
        p.color = Color.WHITE
        val outletX = 300f + variant * 250f
        canvas.drawRect(outletX, PHOTO_H - 260f, outletX + 60f, PHOTO_H - 160f, p) // outlet
        p.color = Color.rgb(90, 70, 50)
        canvas.drawRect(620f, 260f, 980f, 560f, p) // picture frame
        p.color = Color.rgb(170, 190, 210)
        canvas.drawRect(645f, 285f, 955f, 535f, p)
        return bitmap
    }

    private val PALETTE = intArrayOf(
        Color.rgb(214, 205, 186), Color.rgb(188, 204, 196), Color.rgb(201, 189, 210),
        Color.rgb(222, 214, 199), Color.rgb(176, 196, 214), Color.rgb(219, 196, 178),
    )
}

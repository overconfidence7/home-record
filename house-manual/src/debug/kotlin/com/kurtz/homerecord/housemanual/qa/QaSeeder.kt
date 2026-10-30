package com.kurtz.homerecord.housemanual.qa

import android.content.Context
import com.kurtz.homerecord.housemanual.HomeRecordApplication
import java.io.File

/** Debug-only: the seed scenarios, shared by [SeedReceiver] (adb broadcast) and [SeedActivity] (Maestro `openLink`). */
object QaSeeder {
    /** Runs [scenario] (`regression`, `long-list` or `reset`); false for an unknown one. Throws when a scenario fails. */
    suspend fun run(context: Context, scenario: String): Boolean {
        val container = (context.applicationContext as HomeRecordApplication).container
        val documents = File(context.filesDir, "documents")
        return when (scenario) {
            "regression" -> {
                RegressionSeed.seed(container.db, container.homes, documentsDir = documents, pdf = samplePdf(context))
                true
            }
            // AC-U11 scroll jank: the regression home with 200 items on Items
            "long-list" -> {
                RegressionSeed.seedLongList(container.db, container.homes, documentsDir = documents, pdf = samplePdf(context))
                true
            }
            "reset" -> {
                RegressionSeed.reset(container.db)
                documents.deleteRecursively()
                true
            }
            else -> false
        }
    }

    private fun samplePdf(context: Context) = context.assets.open("qa/sample.pdf").use { it.readBytes() }
}

package com.kurtz.homerecord.housemanual.qa

import android.content.Context
import com.kurtz.homerecord.housemanual.HomeRecordApplication
import java.io.File

/** Debug-only: the seed scenarios, shared by [SeedReceiver] (adb broadcast) and [SeedActivity] (Maestro `openLink`). */
object QaSeeder {
    /** Runs [scenario] (`regression`, `long-list`, `3d-reference`, `pro` or `reset`); false for an unknown one. Throws when a scenario fails. */
    suspend fun run(context: Context, scenario: String): Boolean {
        val container = (context.applicationContext as HomeRecordApplication).container
        val documents = File(context.filesDir, "documents")
        // Every seeded home has reminders, so since T072c the app would put the system notification prompt over the
        // tabs on its first frame; a flow's clearState also wipes the grant the suite made. A seeded phone counts as
        // already asked, so flows reach the tabs (the ask itself: DailyReminderCheckTest, AC-C50).
        if (scenario != "reset") container.markNotificationsAsked()
        return when (scenario) {
            "regression" -> {
                val homeId = RegressionSeed.seed(container.db, container.homes, context.resources::getString, documentsDir = documents, pdf = samplePdf(context))
                queueForUpload(container, homeId)
                true
            }
            // AC-U11 scroll jank: the regression home with 200 items on Items
            "long-list" -> {
                val homeId = RegressionSeed.seedLongList(container.db, container.homes, context.resources::getString, documentsDir = documents, pdf = samplePdf(context))
                queueForUpload(container, homeId)
                true
            }
            // AC-C24 3D performance: the regression home at ~3,000 sq ft with Finished photos on 20 walls
            "3d-reference" -> {
                val homeId = RegressionSeed.seed(container.db, container.homes, context.resources::getString, documentsDir = documents, pdf = samplePdf(context))
                Reference3DSeed.seed(container.db, container.homes, container.wallPhotos, homeId, File(context.filesDir, "media"))
                queueForUpload(container, homeId)
                true
            }
            // AC-H18 on keyless QA builds: Pro for this app process, so Sign in on HM-Account opens the form (T032b2b)
            "pro" -> {
                container.qaForcePro.value = true
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

    /**
     * The seed writes most rows straight through the DAOs (and an ItemRepository without the app's change hook), so
     * only the home and its plan reached the outbox: a seeded phone that signed in uploaded an empty house, and H17b's
     * second phone, x-share and x-share-viewer opened "No items yet" [judge:hm-pro-on-sync-not-resumed]. Queue every
     * row of the seeded home, as a real phone's writes would be, the way Delete cloud copy re-queues a home.
     */
    private suspend fun queueForUpload(container: com.kurtz.homerecord.housemanual.AppContainer, homeId: String) {
        container.cloudCopy.resetForUpload(homeId)
    }

    private fun samplePdf(context: Context) = context.assets.open("qa/sample.pdf").use { it.readBytes() }
}

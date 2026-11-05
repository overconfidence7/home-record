package com.kurtz.homerecord.fieldlog

import androidx.test.core.app.ApplicationProvider
import com.kurtz.homerecord.coreui.platform.Intents
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Every folder Field Log shares a file from resolves through the FileProvider (judge F15 2026-11-05: the job summary
 * and photo report PDFs in cache/reports crashed the share, AC-F47, AC-F28).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FileProviderPathsTest {
    @Test fun everySharedFolderResolves() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val files = listOf(
            File(context.cacheDir, "reports/Job summary Hale kitchen.pdf"), // AC-F47
            File(context.cacheDir, "reports/Photo report Hale kitchen 2026-11-02 to 2026-11-08.pdf"), // AC-F28
            File(context.cacheDir, "logs/Daily log.pdf"),
            File(context.cacheDir, "change-orders/Change order.pdf"),
            File(context.cacheDir, "exports/Hours.csv"),
            File(context.cacheDir, "downloads/photo.jpg"),
            File(context.filesDir, "media/photo.jpg"),
            File(context.filesDir, "offline-files/plan.pdf"),
        )
        files.forEach { file ->
            file.parentFile?.mkdirs()
            file.writeText("x")
            val uri = Intents.fileUri(context, file)
            assertTrue("$file -> $uri", uri.toString().startsWith("content://"))
        }
    }
}

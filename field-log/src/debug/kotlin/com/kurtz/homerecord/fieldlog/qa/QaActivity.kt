package com.kurtz.homerecord.fieldlog.qa

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.shell.ShellFrame
import androidx.compose.runtime.Composable
import com.kurtz.homerecord.fieldlog.jobs.JobOverviewUiState
import com.kurtz.homerecord.fieldlog.jobs.JobScreen
import com.kurtz.homerecord.fieldlog.jobs.JobTab
import com.kurtz.homerecord.fieldlog.shellTabs
import com.kurtz.homerecord.fieldlog.PreviewPhotos

/**
 * Debug-only host for the screen catalog. Start it with action `com.kurtz.homerecord.fieldlog.qa.OPEN_SCREEN`,
 * extra `screen=<catalog id>` and optionally `landscape=true`. Dark mode and font scale come from the system
 * settings, so the crawler and the device tour flip those instead.
 */
class QaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("landscape", false)) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // Catalog entries reuse the previews' fake photo paths; draw PreviewPhotos for them instead of the broken-image
        // fallback, so the crawler sees photo screens with a photo (QA rule 3). Real media paths load as usual.
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(this).components { add(PreviewPhotoInterceptor) }.build(),
        )
        val entry = ScreenCatalog.find(intent.getStringExtra("screen"))
        setContent {
            CompositionLocalProvider(LocalTagPrefix provides "fl") {
                Box(Modifier.semantics { testTagsAsResourceId = true }) {
                    val tab = entry?.tab
                    val jobTab = entry?.jobTab
                    when {
                        entry == null -> Unit
                        tab != null -> ShellFrame(shellTabs(), tab) { entry.content() }
                        jobTab != null -> CatalogJobFrame(jobTab) { entry.content() }
                        else -> entry.content()
                    }
                }
            }
        }
    }
}

/** FL-Job around one of its tabs, with the catalog's fake job, for the crawler (rule 28 under the job's bar). */
@Composable
internal fun CatalogJobFrame(selected: JobTab, content: @Composable () -> Unit) {
    JobScreen(
        state = JobOverviewUiState(loading = false, name = "Hale kitchen", clientName = "Dana Hale"),
        selected = selected,
        onSelect = {},
        onEvent = {},
        onBack = {},
        photosContent = content,
        filesContent = content,
        logContent = content,
    )
}

/** Answers the previews' fake photo paths with [PreviewPhotos]; every other request goes on to the real loader. */
private object PreviewPhotoInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val image = PreviewPhotos.image(chain.request.data) ?: return chain.proceed()
        return SuccessResult(image, chain.request, DataSource.MEMORY)
    }
}

package com.kurtz.homerecord.coreui.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IntentsTest {
    private val app: android.app.Application = ApplicationProvider.getApplicationContext()

    @Test fun mapUriPinsTheLabelledPoint() {
        assertThat(Intents.mapUri(39.95, -75.16, "Maple St & 4th").toString()).isEqualTo("geo:39.95,-75.16?q=39.95,-75.16(Maple%20St%20%26%204th)")
    }

    @Test fun openMapFiresAViewIntentOrNothingWithoutAMapsApp() {
        Intents.openMap(app, 39.95, -75.16, "Maple St")
        val next = shadowOf(app).nextStartedActivity
        if (next != null) {
            assertThat(next.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(next.data?.scheme).isEqualTo("geo")
        }
    }

    @Test fun openAppSettingsTargetsThisPackage() {
        Intents.openAppSettings(app)
        val next = shadowOf(app).nextStartedActivity
        assertThat(next.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(next.data).isEqualTo(Uri.fromParts("package", app.packageName, null))
    }

    @Test fun shareOfAMissingFileDoesNothing() {
        Intents.shareFile(app, File(app.cacheDir, "gone.pdf"), "application/pdf")
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun shareTextOpensAChooserWithTheText() {
        Intents.shareText(app, "Join QA Electric with code 4821", "Invite")
        val chooser = shadowOf(app).nextStartedActivity
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertThat(send?.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("Join QA Electric with code 4821")
    }

    @Test fun copyWritesTheContentAndReportsAnUnreadableUri() {
        val src = File(app.cacheDir, "warranty.pdf").apply { writeText("%PDF-1.4") }
        val dest = File(app.filesDir, "documents/copy.pdf")
        assertThat(ContentFiles.copy(app, Uri.fromFile(src), dest)).isTrue()
        assertThat(dest.readText()).isEqualTo("%PDF-1.4")
        val missing = File(app.filesDir, "documents/missing.pdf")
        assertThat(ContentFiles.copy(app as Context, Uri.fromFile(File(app.cacheDir, "nope.pdf")), missing)).isFalse()
        assertThat(missing.exists()).isFalse()
    }
}

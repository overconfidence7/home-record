package com.kurtz.homerecord.housemanual

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.housemanual.emergency.EmergencyEntry
import com.kurtz.homerecord.housemanual.emergency.EmergencyTileService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// T071a (AC-H11, tile half): the quick settings tile opens MainActivity asking for HM-Emergency.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class EmergencyEntryTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun theEntryIntentOpensMainActivityAndIsRecognised() {
        val intent = EmergencyEntry.intent(context)
        assertThat(intent.component?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(EmergencyEntry.wants(intent)).isTrue()
        assertThat(EmergencyEntry.wants(Intent(context, MainActivity::class.java))).isFalse()
        assertThat(EmergencyEntry.wants(null)).isFalse()
        // A Maestro launchApp argument arrives as a string
        assertThat(EmergencyEntry.wants(Intent().putExtra(EmergencyEntry.EXTRA, "true"))).isTrue()
        assertThat(EmergencyEntry.wants(Intent().putExtra(EmergencyEntry.EXTRA, false))).isFalse()
    }

    @Test
    fun tappingTheTileStartsTheEmergencyIntent() {
        val tile = Robolectric.setupService(EmergencyTileService::class.java)
        tile.onClick()
        val started = shadowOf(context).nextStartedActivity
        assertThat(started).isNotNull()
        assertThat(started.component?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(EmergencyEntry.wants(started)).isTrue()
    }
}

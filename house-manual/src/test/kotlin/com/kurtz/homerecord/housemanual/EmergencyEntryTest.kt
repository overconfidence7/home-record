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

// T071b (AC-H11): the 2×1 widget says how many shutoffs are recorded and opens HM-Emergency with the tile's extra.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@com.kurtz.homerecord.core.testing.AcceptanceCriterion("AC-H11")
class EmergencyWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun theCountLineAndTheLaunchExtra() {
        assertThat(com.kurtz.homerecord.housemanual.emergency.EmergencyWidget.text(context, null)).isEqualTo("No shutoffs yet")
        assertThat(com.kurtz.homerecord.housemanual.emergency.EmergencyWidget.text(context, 0)).isEqualTo("No shutoffs yet")
        assertThat(com.kurtz.homerecord.housemanual.emergency.EmergencyWidget.text(context, 1)).isEqualTo("1 shutoff")
        assertThat(com.kurtz.homerecord.housemanual.emergency.EmergencyWidget.text(context, 3)).isEqualTo("3 shutoffs")
        assertThat(com.kurtz.homerecord.housemanual.emergency.EmergencyWidget.OPEN_EMERGENCY.name).isEqualTo(EmergencyEntry.EXTRA)
    }
}

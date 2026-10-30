package com.kurtz.homerecord.housemanual

import android.content.ComponentName
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The keyboard resizes the content instead of panning the window: a pan pushed the navigation rail under the status
 * bar in landscape and stacked on the screens' own imePadding (judge:hm-rail-ime-status-bar).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SoftInputModeTest {
    @AcceptanceCriterion("AC-C12")
    @Test fun mainActivityResizesForTheKeyboard() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertThat(info.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
            .isEqualTo(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}

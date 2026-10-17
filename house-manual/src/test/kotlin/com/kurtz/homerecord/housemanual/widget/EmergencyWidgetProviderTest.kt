package com.kurtz.homerecord.housemanual.widget

import android.app.Application
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.housemanual.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class EmergencyWidgetProviderTest {
    private fun countText(count: Int): String {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = EmergencyWidgetProvider.buildViews(context, count).apply(context, FrameLayout(context))
        return view.findViewById<TextView>(R.id.widget_count).text.toString()
    }

    @Test fun showsPluralCount() = assertEquals("3 shutoffs", countText(3))

    @Test fun showsSingularCount() = assertEquals("1 shutoff", countText(1))

    @Test fun showsZero() = assertEquals("0 shutoffs", countText(0))
}

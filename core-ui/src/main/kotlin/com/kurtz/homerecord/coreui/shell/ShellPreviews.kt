package com.kurtz.homerecord.coreui.shell

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.rounded.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.compose.ui.tooling.preview.Preview
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme

/** A 360 dp phone, the narrowest the bar must fit; 200% shows long labels ("Schedule") on one line. */
@Preview(name = "Light", widthDp = 360, heightDp = 640, showBackground = true)
@Preview(name = "Dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "200% font", widthDp = 360, heightDp = 640, fontScale = 2f, showBackground = true)
annotation class ShellPreviews

@ShellPreviews
@Composable
fun ShellNavBarPreview() = HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
    val tabs = listOf(
        AppTab("today", "Today", Icons.Rounded.Today, Icons.Rounded.Today, PlaceholderKey("Today")),
        AppTab("jobs", "Jobs", Icons.Outlined.Work, Icons.Outlined.Work, PlaceholderKey("Jobs")),
        AppTab("clock", "Clock", Icons.Outlined.Schedule, Icons.Outlined.Schedule, PlaceholderKey("Clock")),
        AppTab("schedule", "Schedule", Icons.Outlined.CalendarMonth, Icons.Outlined.CalendarMonth, PlaceholderKey("Schedule")),
        AppTab("more", "More", Icons.Outlined.MoreHoriz, Icons.Outlined.MoreHoriz, PlaceholderKey("More")),
    )
    // Previews have no Activity, so NavDisplay's back handling needs a dispatcher of its own.
    val owner = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
        HomeRecordApp(tabs, entries = { nav ->
            entry<PlaceholderKey> { key -> PlaceholderRoot(key.title, { nav.push(key.copy(depth = 1)) }) }
        })
    }
}

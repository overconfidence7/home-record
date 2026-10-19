package com.kurtz.homerecord.fieldlog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.coreui.shell.AppTab
import com.kurtz.homerecord.coreui.shell.HomeRecordApp
import com.kurtz.homerecord.coreui.shell.PlaceholderDetail
import com.kurtz.homerecord.coreui.shell.PlaceholderKey
import com.kurtz.homerecord.coreui.shell.PlaceholderRoot

private data class TabDef(val id: String, val label: Int, val icon: ImageVector, val selected: ImageVector)

private fun tab(id: String, label: Int, icon: ImageVector, selected: ImageVector) = TabDef(id, label, icon, selected)

private val tabDefs = listOf(
        tab("today", R.string.tab_today, Icons.Outlined.Today, Icons.Rounded.Today),
        tab("jobs", R.string.tab_jobs, Icons.Outlined.Work, Icons.Rounded.Work),
        tab("clock", R.string.tab_clock, Icons.Outlined.Schedule, Icons.Rounded.Schedule),
        tab("schedule", R.string.tab_schedule, Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
        tab("more", R.string.tab_more, Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz),
)

@Composable
fun FieldLogApp() {
    val tabs = tabDefs.map { AppTab(it.id, stringResource(it.label), it.icon, it.selected, PlaceholderKey(it.id)) }
    val titles = tabDefs.associate { it.id to stringResource(it.label) }
    HomeRecordApp(tabs = tabs, entries = { nav ->
        entry<PlaceholderKey> { key ->
            val title = titles[key.title] ?: key.title
            if (key.depth == 0) {
                PlaceholderRoot(title, onOpenDetail = { nav.push(key.copy(depth = 1)) })
            } else {
                PlaceholderDetail(title, onBack = nav::pop, onOpenDetail = { nav.push(key.copy(depth = key.depth + 1)) })
            }
        }
    })
}

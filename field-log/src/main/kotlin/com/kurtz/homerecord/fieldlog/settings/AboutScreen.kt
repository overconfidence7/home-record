package com.kurtz.homerecord.fieldlog.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R

/** FL-About (T065a): the app's version for support calls, and what the handoff gives the homeowner. Read only. */
data class AboutUiState(val versionName: String, val versionCode: Int)

sealed interface AboutEvent {
    data object Back : AboutEvent
}

@Composable
fun AboutScreen(state: AboutUiState, onEvent: (AboutEvent) -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(title = stringResource(R.string.about_title), onBack = { onEvent(AboutEvent.Back) }) { padding ->
        Column(modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
            )
            RecordListItem(
                headline = stringResource(R.string.about_version),
                supporting = stringResource(R.string.about_version_detail, state.versionName, state.versionCode),
                leading = { Icon(Icons.Rounded.Info, contentDescription = null) },
                trailing = null,
                modifier = Modifier.testTag("fl_about_version"),
            )
            RecordListItem(
                headline = stringResource(R.string.about_house_manual),
                supporting = stringResource(R.string.about_house_manual_detail),
                supportingMaxLines = Int.MAX_VALUE,
                leading = { Icon(Icons.Rounded.Home, contentDescription = null) },
                trailing = null,
                modifier = Modifier.testTag("fl_about_house_manual"),
            )
        }
    }
}

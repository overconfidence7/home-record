package com.kurtz.homerecord.housemanual.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R

/** HM-About (T077): the app's version for support, where the home is kept, and how a contractor's handoff arrives. Read only. */
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
            AboutRow(stringResource(R.string.about_version), stringResource(R.string.about_version_detail, state.versionName, state.versionCode), "hm_about_version") {
                Icon(Icons.Outlined.Info, contentDescription = null)
            }
            AboutRow(stringResource(R.string.about_local), stringResource(R.string.about_local_detail), "hm_about_local") {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null)
            }
            AboutRow(stringResource(R.string.about_field_log), stringResource(R.string.about_field_log_detail), "hm_about_field_log") {
                Icon(Icons.Outlined.Construction, contentDescription = null)
            }
        }
    }
}

@Composable
private fun AboutRow(headline: String, supporting: String, tag: String, leading: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        leadingContent = leading,
        modifier = Modifier.testTag(tag),
    )
}

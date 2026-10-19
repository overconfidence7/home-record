package com.kurtz.homerecord.coreui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.kurtz.homerecord.coreui.R
import kotlinx.serialization.Serializable

/** Stand-in root and child screens until each tab's real screens land. */
@Serializable
data class PlaceholderKey(val title: String, val depth: Int = 0) : NavKey

@Composable
fun PlaceholderRoot(title: String, onOpenDetail: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.placeholder_coming_soon), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onOpenDetail) { Text(stringResource(R.string.placeholder_open_detail)) }
    }
}

@Composable
fun PlaceholderDetail(title: String, onBack: () -> Unit, onOpenDetail: () -> Unit) {
    ScreenScaffold(title = title, onBack = onBack) { padding ->
        PlaceholderRoot(title, onOpenDetail, Modifier.padding(padding))
    }
}

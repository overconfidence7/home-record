package com.kurtz.homerecord.coreui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import com.kurtz.homerecord.coreui.R

/** Empty list: icon, one headline, one sentence, one action (AC-U4). No action for someone who may only view (AC-F3). */
@Composable
fun EmptyState(
    icon: ImageVector,
    headline: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
) {
    CenteredColumn(modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(headline, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction, modifier = actionModifier) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    CenteredColumn(modifier.semantics { contentDescription = label }) {
        CircularProgressIndicator()
    }
}

/** Error: what failed and a Retry (AC-U4). */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredColumn(modifier) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun CenteredColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) { content() }
}

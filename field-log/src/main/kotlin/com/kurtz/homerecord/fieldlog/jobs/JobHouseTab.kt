package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.fieldlog.R

/** FL-House before the job has a home: one sentence and one action that creates it (AC-F48). */
@Composable
fun JobHouseEmpty(onStart: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Rounded.Home,
        headline = stringResource(R.string.jobhouse_empty_title),
        body = stringResource(R.string.jobhouse_empty_body),
        actionLabel = stringResource(R.string.jobhouse_start),
        onAction = onStart,
        modifier = modifier,
        actionModifier = Modifier.testTag("fl_jobhouse_start"),
    )
}

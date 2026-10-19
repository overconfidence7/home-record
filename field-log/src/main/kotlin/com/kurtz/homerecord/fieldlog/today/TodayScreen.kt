package com.kurtz.homerecord.fieldlog.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.fieldlog.R

data class TodayUiState(val companyName: String, val role: CompanyRole)

/** FL-Today. Jobs, the clock card and tasks arrive with later tasks; until then it explains what to do first (AC-F1). */
@Composable
fun TodayScreen(state: TodayUiState, onInviteCrew: () -> Unit, modifier: Modifier = Modifier) {
    if (state.role.isAdmin) {
        EmptyState(
            icon = Icons.Rounded.Work,
            headline = stringResource(R.string.today_empty_title, state.companyName),
            body = stringResource(R.string.today_empty_admin),
            actionLabel = stringResource(R.string.today_invite_crew),
            onAction = onInviteCrew,
            modifier = modifier,
        )
    } else {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Work, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.today_empty_title, state.companyName), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(stringResource(R.string.today_empty_crew), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

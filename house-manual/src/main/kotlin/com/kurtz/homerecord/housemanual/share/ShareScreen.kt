package com.kurtz.homerecord.housemanual.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.share.HomePerson
import com.kurtz.homerecord.core.share.HomeRole
import com.kurtz.homerecord.core.share.InviteFailure
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val untilFormat = DateTimeFormatter.ofPattern("MMMM d", Locale.US)

private fun dayOf(epochMs: Long, zone: ZoneId): String = untilFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

@Composable
private fun roleLabel(role: HomeRole): String = stringResource(
    when (role) {
        HomeRole.Owner -> R.string.share_role_owner
        HomeRole.Editor -> R.string.share_role_editor
        HomeRole.Viewer -> R.string.share_role_viewer
    },
)

/**
 * HM-Share (AC-H16): More → Share this house. The owner invites family (they can change things) or a viewer (a buyer
 * or house-sitter, who can only look) with a one-person link sent through the share sheet, sees who is on the home,
 * removes people (asks first) and turns off links nobody has used. Everyone else sees the list.
 */
@Composable
fun ShareScreen(state: ShareUiState, onEvent: (ShareEvent) -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    ScreenScaffold(title = stringResource(R.string.share_title), onBack = { onEvent(ShareEvent.Back) }) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when (state.phase) {
            SharePhase.SignedOut -> EmptyState(
                icon = Icons.Rounded.Group,
                headline = stringResource(R.string.share_signed_out_title),
                body = stringResource(R.string.share_signed_out_body),
                actionLabel = stringResource(R.string.share_sign_in),
                onAction = { onEvent(ShareEvent.SignIn) },
                modifier = modifier.testTag("hm_share_signed_out"),
                actionModifier = Modifier.heightIn(min = 48.dp).testTag("hm_share_sign_in"),
            )
            SharePhase.Loading -> LoadingState(modifier)
            SharePhase.NotUploaded -> EmptyState(
                icon = Icons.Rounded.CloudUpload,
                headline = stringResource(R.string.share_not_uploaded_title),
                body = stringResource(R.string.share_not_uploaded_body),
                actionLabel = stringResource(com.kurtz.homerecord.coreui.R.string.retry),
                onAction = { onEvent(ShareEvent.Retry) },
                modifier = modifier.testTag("hm_share_not_uploaded"),
                actionModifier = Modifier.heightIn(min = 48.dp).testTag("hm_share_retry"),
            )
            SharePhase.Failed -> ErrorState(
                stringResource(if (state.offline) R.string.share_offline else R.string.share_failed),
                onRetry = { onEvent(ShareEvent.Retry) },
                modifier = modifier.testTag("hm_share_error"),
            )
            SharePhase.Loaded -> Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                if (state.isOwner) {
                    Text(
                        stringResource(R.string.share_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    InviteRow(
                        R.string.share_invite_editor, R.string.share_invite_editor_supporting, Icons.Rounded.PersonAdd,
                        busy = state.creating == HomeRole.Editor, enabled = state.creating == null, tag = "hm_share_invite_editor",
                    ) { onEvent(ShareEvent.Invite(HomeRole.Editor)) }
                    InviteRow(
                        R.string.share_invite_viewer, R.string.share_invite_viewer_supporting, Icons.Outlined.Visibility,
                        busy = state.creating == HomeRole.Viewer, enabled = state.creating == null, tag = "hm_share_invite_viewer",
                    ) { onEvent(ShareEvent.Invite(HomeRole.Viewer)) }
                }
                if (state.actionFailed) {
                    Text(
                        stringResource(if (state.offline) R.string.share_action_offline else R.string.share_action_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("hm_share_action_error"),
                    )
                }
                SectionHeader(stringResource(R.string.share_people))
                state.people.forEachIndexed { i, person ->
                    PersonRow(person, canRemove = state.isOwner && !person.you && person.role != HomeRole.Owner, index = i) {
                        onEvent(ShareEvent.Remove(person))
                    }
                }
                if (!state.isOwner) {
                    Text(
                        stringResource(R.string.share_not_owner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("hm_share_not_owner"),
                    )
                }
                if (state.isOwner && state.invites.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.share_open_links))
                    state.invites.forEachIndexed { i, invite ->
                        RecordListItem(
                            headline = stringResource(if (invite.role == HomeRole.Viewer) R.string.share_link_viewer else R.string.share_link_editor),
                            supporting = stringResource(R.string.share_link_until, dayOf(invite.expiresAt, zone)),
                            leading = { Icon(Icons.Rounded.Link, contentDescription = null) },
                            trailing = {
                                TextButton(
                                    onClick = { onEvent(ShareEvent.Revoke(invite.id)) },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_share_revoke_$i"),
                                ) { Text(stringResource(R.string.share_revoke)) }
                            },
                            modifier = Modifier.testTag("hm_share_link_$i"),
                        )
                    }
                }
            }
        }
    }
    val removing = state.confirmRemove
    if (removing != null) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(ShareEvent.DismissRemove) },
            title = { Text(stringResource(R.string.share_remove_title, removing.name ?: removing.email)) },
            text = { Text(stringResource(R.string.share_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(ShareEvent.ConfirmRemove) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_share_remove_confirm"),
                ) { Text(stringResource(R.string.share_remove)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(ShareEvent.DismissRemove) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_share_remove_cancel"),
                ) { Text(stringResource(com.kurtz.homerecord.coreui.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun InviteRow(headline: Int, supporting: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, busy: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    RecordListItem(
        headline = stringResource(headline),
        supporting = stringResource(supporting),
        supportingMaxLines = 3,
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailing = if (busy) ({ CircularProgressIndicator(Modifier.padding(4.dp)) }) else null,
        onClick = if (enabled) onClick else null,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun PersonRow(person: HomePerson, canRemove: Boolean, index: Int, onRemove: () -> Unit) {
    val name = person.name ?: person.email
    val role = roleLabel(person.role)
    RecordListItem(
        headline = if (person.you) stringResource(R.string.share_you, name) else name,
        supporting = if (person.name != null) "${person.email} · $role" else role,
        leading = { Icon(Icons.Rounded.Person, contentDescription = null) },
        trailing = if (canRemove) {
            {
                IconButton(onClick = onRemove, modifier = Modifier.testTag("hm_share_remove_$index")) {
                    Icon(Icons.Rounded.PersonRemove, contentDescription = stringResource(R.string.share_remove_cd, name))
                }
            }
        } else null,
        modifier = Modifier.testTag("hm_share_person_$index"),
    )
}

/**
 * HM-Join (AC-H16): an invite link opened on this phone. Signed out, one line over the shared sign-in form ([signIn]);
 * then a spinner while the link is accepted and the home comes down, or what went wrong.
 */
@Composable
fun JoinScreen(state: JoinUiState, onEvent: (JoinEvent) -> Unit, signIn: @Composable (Modifier) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.join_title), onBack = { onEvent(JoinEvent.Close) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (state.phase) {
                JoinPhase.SignIn -> {
                    Text(
                        stringResource(R.string.join_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).testTag("hm_join_intro"),
                    )
                    signIn(Modifier.weight(1f))
                }
                JoinPhase.Checking, JoinPhase.Joining, JoinPhase.Restoring, JoinPhase.Done -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("hm_join_working"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator()
                    Text(
                        if (state.phase == JoinPhase.Restoring || state.phase == JoinPhase.Done) stringResource(R.string.join_restoring, state.homeName)
                        else stringResource(R.string.join_joining),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                JoinPhase.OtherHome -> EmptyState(
                    icon = Icons.Rounded.Home,
                    headline = stringResource(R.string.join_other_home_title, state.homeName),
                    body = stringResource(R.string.join_other_home_body, state.homeName),
                    actionLabel = stringResource(com.kurtz.homerecord.coreui.R.string.close),
                    onAction = { onEvent(JoinEvent.Close) },
                    modifier = Modifier.testTag("hm_join_other_home"),
                    actionModifier = Modifier.heightIn(min = 48.dp).testTag("hm_join_close"),
                )
                JoinPhase.Failed -> EmptyState(
                    icon = Icons.Rounded.LinkOff,
                    headline = stringResource(R.string.join_failed_title),
                    body = stringResource(
                        when {
                            state.restoreFailed -> R.string.join_restore_failed
                            state.failure == InviteFailure.Used -> R.string.join_used
                            state.failure == InviteFailure.Expired -> R.string.join_expired
                            state.failure == InviteFailure.NotFound -> R.string.join_not_found
                            state.failure == InviteFailure.Offline -> R.string.join_offline
                            else -> R.string.join_failed
                        },
                    ),
                    actionLabel = stringResource(if (state.canRetry) com.kurtz.homerecord.coreui.R.string.retry else com.kurtz.homerecord.coreui.R.string.close),
                    onAction = { onEvent(if (state.canRetry) JoinEvent.Retry else JoinEvent.Close) },
                    modifier = Modifier.testTag("hm_join_error"),
                    actionModifier = Modifier.heightIn(min = 48.dp).testTag(if (state.canRetry) "hm_join_retry" else "hm_join_close"),
                    secondaryAction = if (state.canRetry) {
                        {
                            TextButton(
                                onClick = { onEvent(JoinEvent.Close) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("hm_join_close"),
                            ) { Text(stringResource(com.kurtz.homerecord.coreui.R.string.close)) }
                        }
                    } else null,
                )
            }
        }
    }
}

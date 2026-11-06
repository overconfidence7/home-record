package com.kurtz.homerecord.housemanual.share

import com.kurtz.homerecord.core.share.HomePerson
import com.kurtz.homerecord.core.share.HomeRole
import com.kurtz.homerecord.core.share.InviteFailure
import com.kurtz.homerecord.core.share.OpenInvite

/** Where HM-Share stands (AC-H16). */
enum class SharePhase {
    /** Sharing goes through the account: sign in first. */
    SignedOut,
    Loading,
    Loaded,
    /** The home is not on the server yet (the first sync has not finished). */
    NotUploaded,
    Failed,
}

/** HM-Share: More → Share this house. */
data class ShareUiState(
    val phase: SharePhase = SharePhase.Loading,
    val people: List<HomePerson> = emptyList(),
    val invites: List<OpenInvite> = emptyList(),
    val offline: Boolean = false,
    /** The link being made, so its button shows progress and both invite buttons wait. */
    val creating: HomeRole? = null,
    /** Making or revoking a link, or removing someone, did not reach the server. */
    val actionFailed: Boolean = false,
    /** Asks before taking someone off the home. */
    val confirmRemove: HomePerson? = null,
) {
    /** Only the owner invites, removes and sees the open links. */
    val isOwner: Boolean get() = people.any { it.you && it.role == HomeRole.Owner }
}

sealed interface ShareEvent {
    data object Back : ShareEvent
    data object SignIn : ShareEvent
    data object Retry : ShareEvent
    data class Invite(val role: HomeRole) : ShareEvent
    data class Revoke(val inviteId: String) : ShareEvent
    data class Remove(val person: HomePerson) : ShareEvent
    data object ConfirmRemove : ShareEvent
    data object DismissRemove : ShareEvent
}

/** Where opening an invite link stands (AC-H16). */
enum class JoinPhase {
    /** The saved session is still being read. */
    Checking,
    /** The link needs a House Manual account; the sign-in form shows. */
    SignIn,
    Joining,
    /** Joined; the shared home is coming down to this phone. */
    Restoring,
    /** Joined, but this phone already keeps another home, so nothing was pulled over it. */
    OtherHome,
    Failed,
    /** The shared home is this phone's home; the tabs take over. */
    Done,
}

data class JoinUiState(
    val phase: JoinPhase = JoinPhase.Checking,
    val homeName: String = "",
    val role: HomeRole? = null,
    val failure: InviteFailure? = null,
    /** The pull after joining failed; Retry runs it again. */
    val restoreFailed: Boolean = false,
) {
    /** Offline and server errors can be retried; a wrong, used or expired link cannot. */
    val canRetry: Boolean get() = restoreFailed || failure == InviteFailure.Offline || failure == InviteFailure.Failed
}

sealed interface JoinEvent {
    data object Retry : JoinEvent
    data object Close : JoinEvent
}

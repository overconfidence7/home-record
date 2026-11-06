package com.kurtz.homerecord.core.sync

import com.kurtz.homerecord.core.auth.AccountHome
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult

/** What signing in on a phone with no home found (AC-H15 second phone). */
sealed interface CloudRestoreResult {
    /** The account's home was pulled down; it is now this phone's home. */
    data class Restored(val homeId: String) : CloudRestoreResult
    /** The account has no home yet: set one up here and it syncs. */
    data object NoHome : CloudRestoreResult
    /** This phone already has a home; nothing was pulled over it. */
    data object AlreadyHasHome : CloudRestoreResult
    data class Failed(val failure: ApiFailure) : CloudRestoreResult
}

/**
 * Brings the signed-in account's home to a phone that has none (AC-H15: "a second device signed in to the same account
 * shows the same home"). House Manual's sync tenant is the phone's own home, so a fresh install has nothing to sync;
 * this asks `GET /me` for the account's homes, picks the one it owns (then any other, by name), and runs one sync pass
 * for it, which pulls every row. The home row arriving makes it this phone's home, and from then on the normal sync and
 * photo passes carry on. A phone that already has a home is left alone.
 */
class CloudHomeRestore(
    private val homesOnAccount: suspend () -> ApiResult<List<AccountHome>>,
    private val localHomeIds: suspend () -> List<String>,
    private val sync: suspend (TenantRef) -> ApiResult<*>,
) {
    /** [preferHomeId]: the home an invite link just joined (AC-H16), taken over the account's own. */
    suspend fun restore(preferHomeId: String? = null): CloudRestoreResult {
        if (localHomeIds().isNotEmpty()) return CloudRestoreResult.AlreadyHasHome
        val homes = when (val result = homesOnAccount()) {
            is ApiResult.Ok -> result.value
            is ApiResult.Err -> return CloudRestoreResult.Failed(result.failure)
        }
        val home = homes.firstOrNull { it.id == preferHomeId } ?: pick(homes) ?: return CloudRestoreResult.NoHome
        val synced = sync(TenantRef(TenantKind.Home, home.id))
        if (synced is ApiResult.Err) return CloudRestoreResult.Failed(synced.failure)
        // The server knew the membership but sent no home row (an empty or half-deleted home): nothing to show.
        return if (home.id in localHomeIds()) CloudRestoreResult.Restored(home.id) else CloudRestoreResult.NoHome
    }

    companion object {
        /** The owned home first, then any shared one; ties by name so the choice is stable. */
        fun pick(homes: List<AccountHome>): AccountHome? =
            homes.sortedWith(compareBy<AccountHome> { if (it.role == "owner") 0 else 1 }.thenBy { it.name.lowercase() }.thenBy { it.id })
                .firstOrNull()
    }
}

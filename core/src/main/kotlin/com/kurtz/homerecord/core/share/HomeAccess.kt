package com.kurtz.homerecord.core.share

import com.kurtz.homerecord.core.auth.AccountHome
import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** This phone's role on each home it holds, kept across restarts so view-only holds offline (AC-H16). */
interface HomeRoleStore {
    fun get(homeId: String): HomeRole?
    fun put(homeId: String, role: HomeRole)
}

class PrefsHomeRoleStore(private val prefs: android.content.SharedPreferences) : HomeRoleStore {
    override fun get(homeId: String): HomeRole? = prefs.getString(homeId, null)?.let { stored -> HomeRole.entries.firstOrNull { it.name == stored } }
    override fun put(homeId: String, role: HomeRole) { prefs.edit().putString(homeId, role.name).apply() }
}

class MemoryHomeRoleStore : HomeRoleStore {
    private val roles = mutableMapOf<String, HomeRole>()
    override fun get(homeId: String): HomeRole? = roles[homeId]
    override fun put(homeId: String, role: HomeRole) { roles[homeId] = role }
}

/**
 * Whether this phone may change a home (AC-H16: "a viewer cannot edit (edit controls hidden, not just disabled)").
 * The role comes from the server: when an invite link is accepted, when a home is pulled down, and after every sync
 * pass ([refresh] reads `GET /me`). A home this phone made, or one never seen on the server, is the phone's own and
 * editable; a viewer stays view-only offline and after signing out, because the copy on the phone is the owner's home.
 */
class HomeAccess(
    private val store: HomeRoleStore,
    private val homesOnAccount: suspend () -> ApiResult<List<AccountHome>>,
) {
    private val changes = MutableStateFlow(0)

    fun role(homeId: String): HomeRole? = store.get(homeId)

    fun canEdit(homeId: String): Boolean = role(homeId)?.canEdit ?: true

    fun observeCanEdit(homeId: String): Flow<Boolean> = changes.map { canEdit(homeId) }.distinctUntilChanged()

    fun remember(homeId: String, role: HomeRole) {
        if (store.get(homeId) == role) return
        store.put(homeId, role)
        changes.value += 1
    }

    /**
     * Reads the account's homes and keeps [homeId]'s role. A failed call or a home not on the account changes nothing:
     * the last known role holds (offline, or the owner's home not uploaded yet).
     */
    suspend fun refresh(homeId: String) {
        val homes = (homesOnAccount() as? ApiResult.Ok)?.value ?: return
        homes.firstOrNull { it.id == homeId }?.let { remember(homeId, HomeRole.parse(it.role)) }
    }
}

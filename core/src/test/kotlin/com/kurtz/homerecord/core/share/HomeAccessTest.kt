package com.kurtz.homerecord.core.share

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.auth.AccountHome
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.sync.CloudHomeRestore
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

// T076c: AC-H16 "a viewer cannot edit": the phone keeps its role on the home, from the invite, the restore and each sync.
@AcceptanceCriterion("AC-H16")
class HomeAccessTest {
    private var account: ApiResult<List<AccountHome>> = ApiResult.Ok(emptyList())
    private val access = HomeAccess(MemoryHomeRoleStore()) { account }

    @Test fun aHomeNeverSeenOnTheServerIsEditable() = runTest {
        assertThat(access.canEdit("h1")).isTrue()
        assertThat(access.observeCanEdit("h1").first()).isTrue()
    }

    @Test fun aViewerCannotEditAndFamilyCan() = runTest {
        access.remember("h1", HomeRole.Viewer)
        assertThat(access.observeCanEdit("h1").first()).isFalse()
        access.remember("h1", HomeRole.Editor)
        assertThat(access.observeCanEdit("h1").first()).isTrue()
    }

    @Test fun refreshTakesTheRoleFromTheAccountAndKeepsItWhenOffline() = runTest {
        account = ApiResult.Ok(listOf(AccountHome("h1", "Maple St", "viewer"), AccountHome("h2", "Lake house", "owner")))
        access.refresh("h1")
        assertThat(access.role("h1")).isEqualTo(HomeRole.Viewer)
        account = ApiResult.Err(ApiFailure.Offline)
        access.refresh("h1")
        assertThat(access.canEdit("h1")).isFalse()
        // The owner made them family since.
        account = ApiResult.Ok(listOf(AccountHome("h1", "Maple St", "editor")))
        access.refresh("h1")
        assertThat(access.canEdit("h1")).isTrue()
    }

    @Test fun aHomeNotOnTheAccountKeepsItsLastRole() = runTest {
        access.remember("h1", HomeRole.Owner)
        account = ApiResult.Ok(emptyList())
        access.refresh("h1")
        assertThat(access.role("h1")).isEqualTo(HomeRole.Owner)
    }

    @Test fun restoringASharedHomeRecordsTheRoleBeforeItsRowsArrive() = runTest {
        var roleWhenSyncing: HomeRole? = null
        val restore = CloudHomeRestore(
            homesOnAccount = { ApiResult.Ok(listOf(AccountHome("h1", "Maple St", "viewer"))) },
            localHomeIds = { emptyList() },
            sync = { roleWhenSyncing = access.role(it.id); ApiResult.Ok(Unit) },
            onPicked = { access.remember(it.id, HomeRole.parse(it.role)) },
        )
        restore.restore()
        assertThat(roleWhenSyncing).isEqualTo(HomeRole.Viewer)
    }
}

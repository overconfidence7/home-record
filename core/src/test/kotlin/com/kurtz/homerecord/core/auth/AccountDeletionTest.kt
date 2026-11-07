package com.kurtz.homerecord.core.auth

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Test

// T081d: the phone cleans up only after the server deleted the account.
class AccountDeletionTest {
    @Test fun aDeletedAccountRunsTheAppsCleanUp() = runTest {
        var cleaned = 0
        val result = AccountDeletion({ ApiResult.Ok(Unit) }) { cleaned++ }.delete()
        assertThat(result).isEqualTo(ApiResult.Ok(Unit))
        assertThat(cleaned).isEqualTo(1)
    }

    @Test fun aFailedDeleteLeavesThePhoneAlone() = runTest {
        var cleaned = 0
        val result = AccountDeletion({ ApiResult.Err(ApiFailure.Offline) }) { cleaned++ }.delete()
        assertThat(result).isEqualTo(ApiResult.Err(ApiFailure.Offline))
        assertThat(cleaned).isEqualTo(0)
    }
}

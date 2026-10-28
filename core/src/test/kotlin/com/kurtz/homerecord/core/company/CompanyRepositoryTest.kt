package com.kurtz.homerecord.core.company

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakeCompanyApi : CompanyApi {
    var memberships: ApiResult<List<CompanyMembership>> = ApiResult.Ok(emptyList())
    var createResult: ApiResult<String> = ApiResult.Ok("c1")
    var joinResult: ApiResult<String> = ApiResult.Ok("c1")
    var onCreate: () -> Unit = {}
    var onJoin: () -> Unit = {}
    override suspend fun me() = memberships
    override suspend fun create(name: String, trade: String, phone: String) = createResult.also { onCreate() }
    override suspend fun join(code: String) = joinResult.also { onJoin() }
    override suspend fun members(companyId: String): ApiResult<List<TeamMember>> = ApiResult.Ok(emptyList())
    override suspend fun invite(companyId: String): ApiResult<String> = ApiResult.Ok("K7M2QX9R")
    override suspend fun rotateInvite(companyId: String): ApiResult<String> = ApiResult.Ok("NEWCODE2")
    override suspend fun setRole(companyId: String, userId: String, role: CompanyRole): ApiResult<Unit> = ApiResult.Ok(Unit)
    override suspend fun setRate(companyId: String, userId: String, rateCents: Long?): ApiResult<Unit> = ApiResult.Ok(Unit)
    override suspend fun remove(companyId: String, userId: String): ApiResult<Unit> = ApiResult.Ok(Unit)
    override suspend fun profile(companyId: String): ApiResult<CompanyProfile> = ApiResult.Err(ApiFailure.Failed)
    override suspend fun updateProfile(companyId: String, update: CompanyProfileUpdate): ApiResult<Unit> = ApiResult.Ok(Unit)
}

class FakeCompanyCache(var stored: CompanyMembership? = null) : CompanyCache {
    override fun read() = stored
    override fun write(membership: CompanyMembership?) { stored = membership }
}

private val kurtz = CompanyMembership("c1", "Kurtz Builders", CompanyRole.Owner, 1_900_000_000_000, null)

class CompanyRepositoryTest {
    @Test fun startsLoadingWithNoCache() {
        assertThat(CompanyRepository(FakeCompanyApi(), FakeCompanyCache()).state.value).isEqualTo(CompanyState.Loading)
    }

    @Test fun refreshWithNoMembershipMeansNoCompany() = runTest {
        val repo = CompanyRepository(FakeCompanyApi(), FakeCompanyCache())
        repo.refresh()
        assertThat(repo.state.value).isEqualTo(CompanyState.NoCompany)
    }

    @Test fun createRefreshesAndLandsAsOwner() = runTest {
        val api = FakeCompanyApi()
        api.onCreate = { api.memberships = ApiResult.Ok(listOf(kurtz)) }
        val cache = FakeCompanyCache()
        val repo = CompanyRepository(api, cache)
        assertThat(repo.create("Kurtz Builders", "Remodeling", "555-0100")).isEqualTo(ApiResult.Ok(Unit))
        assertThat(repo.state.value).isEqualTo(CompanyState.Active(kurtz))
        assertThat(repo.current?.role).isEqualTo(CompanyRole.Owner)
        assertThat(cache.stored).isEqualTo(kurtz)
    }

    @Test fun joinWithBadCodeReportsItAndStaysPut() = runTest {
        val api = FakeCompanyApi().apply { joinResult = ApiResult.Err(ApiFailure.InvalidCode) }
        val repo = CompanyRepository(api, FakeCompanyCache())
        repo.refresh()
        assertThat(repo.join("ZZZZZZZZ")).isEqualTo(ApiResult.Err(ApiFailure.InvalidCode))
        assertThat(repo.state.value).isEqualTo(CompanyState.NoCompany)
    }

    @Test fun joinLandsAsCrew() = runTest {
        val crew = kurtz.copy(role = CompanyRole.Crew)
        val api = FakeCompanyApi()
        api.onJoin = { api.memberships = ApiResult.Ok(listOf(crew)) }
        val repo = CompanyRepository(api, FakeCompanyCache())
        repo.join("K7M2QX9R")
        assertThat(repo.current?.role).isEqualTo(CompanyRole.Crew)
    }

    @Test fun offlineKeepsCachedCompany() = runTest {
        val api = FakeCompanyApi().apply { memberships = ApiResult.Err(ApiFailure.Offline) }
        val repo = CompanyRepository(api, FakeCompanyCache(kurtz))
        repo.refresh()
        assertThat(repo.state.value).isEqualTo(CompanyState.Active(kurtz))
    }

    @Test fun offlineWithoutCacheShowsFailure() = runTest {
        val api = FakeCompanyApi().apply { memberships = ApiResult.Err(ApiFailure.Offline) }
        val repo = CompanyRepository(api, FakeCompanyCache())
        repo.refresh()
        assertThat(repo.state.value).isEqualTo(CompanyState.Failed(ApiFailure.Offline))
    }

    @Test fun removedMemberLosesTheCompanyOnNextRefresh() = runTest {
        val api = FakeCompanyApi()
        val repo = CompanyRepository(api, FakeCompanyCache(kurtz))
        repo.refresh()
        assertThat(repo.state.value).isEqualTo(CompanyState.NoCompany)
    }

    @Test fun clearDropsCachedData() {
        val cache = FakeCompanyCache(kurtz)
        val repo = CompanyRepository(FakeCompanyApi(), cache)
        repo.clear()
        assertThat(cache.stored).isNull()
        assertThat(repo.state.value).isEqualTo(CompanyState.Loading)
    }

    // AC-F9, AC-F12, AC-F13, AC-F18 (cr:fl-settings-source): the rules come from the cached membership, so a Crew phone
    // offline still has them, and a refresh replaces them
    @Test fun settingsComeFromTheCachedMembershipAndFollowRefresh() = runTest {
        val strict = CompanySettings(OvertimeRule.Daily8, breakRule = true, requireGps = true, punchDistanceFt = 200)
        val crew = kurtz.copy(role = CompanyRole.Crew, settings = strict)
        val api = FakeCompanyApi().apply { memberships = ApiResult.Err(ApiFailure.Offline) }
        val repo = CompanyRepository(api, FakeCompanyCache(crew))
        repo.refresh()
        assertThat(repo.settings.first()).isEqualTo(strict)
        val relaxed = strict.copy(requireGps = false)
        api.memberships = ApiResult.Ok(listOf(crew.copy(settings = relaxed)))
        repo.refresh()
        assertThat(repo.settings.first()).isEqualTo(relaxed)
    }

    @Test fun settingsAreTheDefaultsWithoutACompany() = runTest {
        assertThat(CompanyRepository(FakeCompanyApi(), FakeCompanyCache()).settings.first()).isEqualTo(CompanySettings())
    }
}

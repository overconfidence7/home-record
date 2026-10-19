package com.kurtz.homerecord.fieldlog.company

import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.CompanyApi
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.company.CompanyProfile
import com.kurtz.homerecord.core.company.CompanyProfileUpdate
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.company.TeamMember

class FakeCompanyApi : CompanyApi {
    var members: ApiResult<List<TeamMember>> = ApiResult.Ok(emptyList())
    var invite: ApiResult<String> = ApiResult.Ok("K7M2QX9R")
    var profile: ApiResult<CompanyProfile> = ApiResult.Ok(CompanyProfile("c1", "Kurtz Electric", "Electrical", "555", null, null, CompanySettings()))
    var action: ApiResult<Unit> = ApiResult.Ok(Unit)
    val calls = mutableListOf<String>()
    var lastUpdate: CompanyProfileUpdate? = null

    override suspend fun me(): ApiResult<List<CompanyMembership>> = ApiResult.Ok(emptyList())
    override suspend fun create(name: String, trade: String, phone: String): ApiResult<String> = ApiResult.Ok("c1")
    override suspend fun join(code: String): ApiResult<String> = ApiResult.Ok("c1")
    override suspend fun members(companyId: String) = members
    override suspend fun invite(companyId: String) = invite
    override suspend fun rotateInvite(companyId: String): ApiResult<String> = ApiResult.Ok("NEWCODE2").also { calls += "rotate" }
    override suspend fun setRole(companyId: String, userId: String, role: CompanyRole) = action.also { calls += "role:$userId:${role.wire}" }
    override suspend fun setRate(companyId: String, userId: String, rateCents: Long?) = action.also { calls += "rate:$userId:$rateCents" }
    override suspend fun remove(companyId: String, userId: String) = action.also { calls += "remove:$userId" }
    override suspend fun profile(companyId: String) = profile
    override suspend fun updateProfile(companyId: String, update: CompanyProfileUpdate) = action.also { lastUpdate = update }
}

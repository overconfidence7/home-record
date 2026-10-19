package com.kurtz.homerecord.core.company

/** Network side of company and team management; [RetrofitCompanyApi] is the real one, tests use a fake. */
interface CompanyApi {
    suspend fun me(): ApiResult<List<CompanyMembership>>
    suspend fun create(name: String, trade: String, phone: String): ApiResult<String>
    suspend fun join(code: String): ApiResult<String>
    suspend fun members(companyId: String): ApiResult<List<TeamMember>>
    suspend fun invite(companyId: String): ApiResult<String>
    suspend fun rotateInvite(companyId: String): ApiResult<String>
    suspend fun setRole(companyId: String, userId: String, role: CompanyRole): ApiResult<Unit>
    suspend fun setRate(companyId: String, userId: String, rateCents: Long?): ApiResult<Unit>
    suspend fun remove(companyId: String, userId: String): ApiResult<Unit>
    suspend fun profile(companyId: String): ApiResult<CompanyProfile>
    suspend fun updateProfile(companyId: String, update: CompanyProfileUpdate): ApiResult<Unit>
}

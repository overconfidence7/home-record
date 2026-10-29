package com.kurtz.homerecord.core.company

import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface CompanyService {
    @GET("me") suspend fun me(): MeResponse

    @POST("companies") suspend fun create(@Body body: CreateCompanyBody): CompanyRef

    @POST("companies/join") suspend fun join(@Body body: JoinBody): CompanyRef

    @GET("companies/{id}/members") suspend fun members(@Path("id") id: String): MembersResponse

    @GET("companies/{id}/invite") suspend fun invite(@Path("id") id: String): InviteBody

    @POST("companies/{id}/invite/rotate") suspend fun rotate(@Path("id") id: String): InviteBody

    @PATCH("companies/{id}/members/{user}") suspend fun patchMember(@Path("id") id: String, @Path("user") user: String, @Body body: RolePatch)

    @DELETE("companies/{id}/members/{user}") suspend fun removeMember(@Path("id") id: String, @Path("user") user: String)

    @GET("companies/{id}/profile") suspend fun profile(@Path("id") id: String): ProfileBody

    @PATCH("companies/{id}/profile") suspend fun patchProfile(@Path("id") id: String, @Body body: ProfileBody)
}

class RetrofitCompanyApi(private val service: CompanyService) : CompanyApi {
    override suspend fun me() = call {
        service.me().memberships.filter { it.kind == "company" }.map {
            CompanyMembership(it.id, it.name, CompanyRole.fromWire(it.role), it.trialEndsAt, it.entitlement, it.settings.toSettings())
        }
    }

    override suspend fun create(name: String, trade: String, phone: String) =
        call { service.create(CreateCompanyBody(name.trim(), trade.trim().ifEmpty { null }, phone.trim().ifEmpty { null })).id }

    override suspend fun join(code: String) = call { service.join(JoinBody(code.trim())).id }

    override suspend fun members(companyId: String) = call {
        service.members(companyId).members.map { TeamMember(it.userId, it.name, it.email, CompanyRole.fromWire(it.role), it.rateCents) }
    }

    override suspend fun invite(companyId: String) = call { service.invite(companyId).code }

    override suspend fun rotateInvite(companyId: String) = call { service.rotate(companyId).code }

    override suspend fun setRole(companyId: String, userId: String, role: CompanyRole) =
        call { service.patchMember(companyId, userId, RolePatch(role = role.wire)) }

    override suspend fun setRate(companyId: String, userId: String, rateCents: Long?) =
        call { service.patchMember(companyId, userId, RolePatch(rateCents = rateCents)) }

    override suspend fun remove(companyId: String, userId: String) = call { service.removeMember(companyId, userId) }

    override suspend fun profile(companyId: String) = call {
        val p = service.profile(companyId)
        CompanyProfile(
            id = p.id ?: companyId,
            name = p.name.orEmpty(),
            trade = p.trade,
            phone = p.phone,
            email = p.email,
            address = p.address,
            settings = p.settings.toSettings(),
        )
    }

    override suspend fun updateProfile(companyId: String, update: CompanyProfileUpdate) = call {
        val s = update.settings
        service.patchProfile(
            companyId,
            ProfileBody(
                name = update.name, trade = update.trade, phone = update.phone, email = update.email, address = update.address,
                settings = s?.let { SettingsBody(it.overtimeRule.wire, it.breakRule, it.requireGps, it.punchDistanceFt, it.photoOnPunch) },
            ),
        )
    }

    private suspend fun <T> call(block: suspend () -> T): ApiResult<T> =
        try {
            ApiResult.Ok(block())
        } catch (e: HttpException) {
            ApiResult.Err(failureFor(e))
        } catch (e: IOException) {
            ApiResult.Err(ApiFailure.Offline)
        }

    private fun failureFor(e: HttpException): ApiFailure {
        val code = e.response()?.errorBody()?.string()?.let { runCatching { json.decodeFromString<com.kurtz.homerecord.core.auth.ErrorBody>(it).error }.getOrNull() }
        return when {
            code == "invalid_code" -> ApiFailure.InvalidCode
            e.code() == 404 -> ApiFailure.NotFound
            e.code() == 403 -> ApiFailure.Forbidden
            e.code() == 400 -> ApiFailure.Invalid
            else -> ApiFailure.Failed
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, client: Call.Factory): RetrofitCompanyApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitCompanyApi(retrofit.create(CompanyService::class.java))
        }
    }
}

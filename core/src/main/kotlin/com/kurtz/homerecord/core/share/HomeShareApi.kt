package com.kurtz.homerecord.core.share

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.net.apiCall
import com.kurtz.homerecord.core.net.failureForStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** A home invite link (AC-H16): `homerecord://invite/<token>` from the link page, or the https link itself. */
object HomeInviteLink {
    private val TOKEN = Regex("^[A-Za-z0-9_-]{32,64}$")

    /** The token of [link], or null when it is not an invite link. */
    fun token(link: String?): String? {
        val s = link?.trim().orEmpty()
        val rest = when {
            s.startsWith("homerecord://invite/") -> s.removePrefix("homerecord://invite/")
            s.startsWith("https://") || s.startsWith("http://") ->
                s.substringAfter("://").substringAfter('/', "").takeIf { it.startsWith("invite/") }?.removePrefix("invite/")
            else -> null
        } ?: return null
        val token = rest.substringBefore('?').substringBefore('#').trimEnd('/')
        return token.takeIf { TOKEN.matches(it) }
    }
}

/** What someone on a shared home may do (AC-H16). */
enum class HomeRole {
    Owner, Editor, Viewer;

    val canEdit: Boolean get() = this != Viewer

    companion object {
        /** The server's role name; the pre-sharing `member` and anything unknown read as Editor, never as Owner. */
        fun parse(value: String?): HomeRole = when (value) {
            "owner" -> Owner
            "viewer" -> Viewer
            else -> Editor
        }
    }
}

/** Someone on the home. */
data class HomePerson(val userId: String, val name: String?, val email: String, val role: HomeRole, val you: Boolean)

/** A link the owner made that nobody has used yet. */
data class OpenInvite(val id: String, val role: HomeRole, val createdAt: Long, val expiresAt: Long)

data class HomePeople(val people: List<HomePerson>, val invites: List<OpenInvite>)

/** A fresh link to send. */
data class NewInvite(val id: String, val role: HomeRole, val link: String, val expiresAt: Long)

/** The home an accepted link joined. */
data class JoinedHome(val homeId: String, val homeName: String, val role: HomeRole)

/** Why accepting a link did not work, beyond the usual offline and server errors. */
enum class InviteFailure { Offline, NotFound, Used, Expired, SignedOut, Failed }

sealed interface AcceptResult {
    data class Joined(val home: JoinedHome) : AcceptResult
    data class Failed(val failure: InviteFailure) : AcceptResult
}

/** HM-Share's backend (backend/src/routes/homeShare.ts). */
interface HomeShareApi {
    suspend fun people(homeId: String): ApiResult<HomePeople>
    suspend fun invite(homeId: String, role: HomeRole): ApiResult<NewInvite>
    suspend fun revoke(homeId: String, inviteId: String): ApiResult<Unit>
    suspend fun remove(homeId: String, userId: String): ApiResult<Unit>
    suspend fun accept(token: String): AcceptResult
}

@Serializable internal data class PersonBody(val userId: String, val name: String? = null, val email: String, val role: String, val you: Boolean = false)
@Serializable internal data class InviteRowBody(val id: String, val role: String, val createdAt: Long = 0, val expiresAt: Long = 0)
@Serializable internal data class PeopleBody(val people: List<PersonBody> = emptyList(), val invites: List<InviteRowBody> = emptyList())
@Serializable internal data class InviteRequest(val role: String)
@Serializable internal data class NewInviteBody(val id: String, val role: String, val link: String, val expiresAt: Long = 0)
@Serializable internal data class AcceptBody(val homeId: String, val homeName: String = "", val role: String)
@Serializable internal data class OkBody(val ok: Boolean = true)

internal interface HomeShareService {
    @GET("homes/{homeId}/people") suspend fun people(@Path("homeId") homeId: String): PeopleBody
    @POST("homes/{homeId}/invites") suspend fun invite(@Path("homeId") homeId: String, @Body body: InviteRequest): NewInviteBody
    @DELETE("homes/{homeId}/invites/{inviteId}") suspend fun revoke(@Path("homeId") homeId: String, @Path("inviteId") inviteId: String): OkBody
    @DELETE("homes/{homeId}/people/{userId}") suspend fun remove(@Path("homeId") homeId: String, @Path("userId") userId: String): OkBody
    @POST("invite/{token}/accept") suspend fun accept(@Path("token") token: String): AcceptBody
}

class RetrofitHomeShareApi internal constructor(private val service: HomeShareService) : HomeShareApi {
    override suspend fun people(homeId: String): ApiResult<HomePeople> = apiCall {
        val body = service.people(homeId)
        HomePeople(
            body.people.map { HomePerson(it.userId, it.name, it.email, HomeRole.parse(it.role), it.you) },
            body.invites.map { OpenInvite(it.id, HomeRole.parse(it.role), it.createdAt, it.expiresAt) },
        )
    }

    override suspend fun invite(homeId: String, role: HomeRole): ApiResult<NewInvite> = apiCall {
        val body = service.invite(homeId, InviteRequest(role.name.lowercase()))
        NewInvite(body.id, HomeRole.parse(body.role), body.link, body.expiresAt)
    }

    override suspend fun revoke(homeId: String, inviteId: String): ApiResult<Unit> = apiCall { service.revoke(homeId, inviteId); Unit }

    override suspend fun remove(homeId: String, userId: String): ApiResult<Unit> = apiCall { service.remove(homeId, userId); Unit }

    override suspend fun accept(token: String): AcceptResult {
        var status: InviteFailure? = null
        val r = apiCall({ e ->
            status = when (e.code()) {
                401 -> InviteFailure.SignedOut
                404 -> InviteFailure.NotFound
                409 -> InviteFailure.Used
                410 -> InviteFailure.Expired
                else -> InviteFailure.Failed
            }
            failureForStatus(e)
        }) { service.accept(token) }
        return when (r) {
            is ApiResult.Ok -> AcceptResult.Joined(JoinedHome(r.value.homeId, r.value.homeName, HomeRole.parse(r.value.role)))
            is ApiResult.Err -> AcceptResult.Failed(status ?: if (r.failure == ApiFailure.Offline) InviteFailure.Offline else InviteFailure.Failed)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, client: Call.Factory): RetrofitHomeShareApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitHomeShareApi(retrofit.create(HomeShareService::class.java))
        }
    }
}

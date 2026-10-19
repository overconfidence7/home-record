package com.kurtz.homerecord.core.company

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Roles from SPEC 6.1. `Sub` is stored but has no app screens yet. */
enum class CompanyRole(val wire: String) {
    Owner("owner"),
    Office("office"),
    Lead("lead"),
    Crew("crew"),
    Sub("sub");

    /** FL-Team, FL-Company, FL-Timesheets and FL-Reports (AC-F3). Hidden, not greyed out, for everyone else. */
    val isAdmin: Boolean get() = this == Owner || this == Office

    /** Roles this role may assign to someone else; mirrors the backend rule (AC-B3). */
    val assignableRoles: List<CompanyRole>
        get() = when (this) {
            Owner -> listOf(Office, Lead, Crew, Sub)
            Office -> listOf(Lead, Crew, Sub)
            else -> emptyList()
        }

    /** Whether this role may change or remove someone who holds [target]. */
    fun canManage(target: CompanyRole): Boolean = when (this) {
        Owner -> target != Owner
        Office -> target == Lead || target == Crew || target == Sub
        else -> false
    }

    companion object {
        fun fromWire(value: String): CompanyRole = entries.firstOrNull { it.wire == value } ?: Crew
    }
}

/** The signed-in user's place in one company, from `GET /me`. */
data class CompanyMembership(
    val companyId: String,
    val companyName: String,
    val role: CompanyRole,
    val trialEndsAt: Long?,
    val entitlement: String?,
)

data class TeamMember(
    val userId: String,
    val name: String?,
    val email: String,
    val role: CompanyRole,
    val rateCents: Long?,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: email.substringBefore('@')
}

enum class OvertimeRule(val wire: String) {
    None("none"),
    Daily8("daily8"),
    Weekly40("weekly40"),
    Daily8Weekly40("daily8weekly40");

    companion object {
        fun fromWire(value: String): OvertimeRule = entries.firstOrNull { it.wire == value } ?: None
    }
}

/** FL-Company settings (SPEC 6.1). Defaults match the backend's. */
data class CompanySettings(
    val overtimeRule: OvertimeRule = OvertimeRule.None,
    val breakRule: Boolean = false,
    val requireGps: Boolean = false,
    val punchDistanceFt: Int = DEFAULT_PUNCH_DISTANCE_FT,
    val photoOnPunch: Boolean = false,
) {
    companion object {
        const val DEFAULT_PUNCH_DISTANCE_FT = 500
        const val MIN_PUNCH_DISTANCE_FT = 50
        const val MAX_PUNCH_DISTANCE_FT = 52_800
    }
}

data class CompanyProfile(
    val id: String,
    val name: String,
    val trade: String?,
    val phone: String?,
    val email: String?,
    val address: String?,
    val settings: CompanySettings,
)

/** Fields the FL-Company form can change; null means leave as is. */
data class CompanyProfileUpdate(
    val name: String? = null,
    val trade: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val settings: CompanySettings? = null,
)

enum class ApiFailure { Offline, NotFound, Forbidden, Invalid, InvalidCode, Failed }

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val failure: ApiFailure) : ApiResult<Nothing>
}

/** Wire types for the company endpoints (backend/src/routes/company.ts and me.ts). */
@Serializable data class MeUser(val id: String, val email: String, val name: String? = null)

@Serializable
data class MeMembership(
    val kind: String,
    val id: String,
    val role: String,
    val name: String,
    val entitlement: String? = null,
    @SerialName("trialEndsAt") val trialEndsAt: Long? = null,
)

@Serializable data class MeResponse(val user: MeUser, val memberships: List<MeMembership>)

@Serializable data class CompanyRef(val id: String, val role: String)

@Serializable data class CreateCompanyBody(val name: String, val trade: String? = null, val phone: String? = null)

@Serializable data class JoinBody(val code: String)

@Serializable data class InviteBody(val code: String)

@Serializable
data class MemberBody(val userId: String, val name: String? = null, val email: String, val role: String, val rateCents: Long? = null)

@Serializable data class MembersResponse(val members: List<MemberBody>)

@Serializable
data class SettingsBody(
    val otRule: String? = null,
    val breakRule: Boolean? = null,
    val requireGps: Boolean? = null,
    val punchDistanceFt: Int? = null,
    val photoOnPunch: Boolean? = null,
)

@Serializable
data class ProfileBody(
    val id: String? = null,
    val name: String? = null,
    val trade: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val settings: SettingsBody? = null,
)

@Serializable data class RolePatch(val role: String? = null, val rateCents: Long? = null)

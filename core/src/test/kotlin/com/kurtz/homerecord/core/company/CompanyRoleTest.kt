package com.kurtz.homerecord.core.company

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompanyRoleTest {
    @Test fun onlyOwnerAndOfficeAreAdmin() {
        assertThat(CompanyRole.entries.filter { it.isAdmin }).containsExactly(CompanyRole.Owner, CompanyRole.Office)
    }

    @Test fun ownerManagesEveryoneButOwners() {
        assertThat(CompanyRole.entries.filter { CompanyRole.Owner.canManage(it) }).containsExactly(
            CompanyRole.Office, CompanyRole.Lead, CompanyRole.Crew, CompanyRole.Sub,
        )
    }

    @Test fun officeCannotTouchOfficeOrOwner() {
        assertThat(CompanyRole.Office.canManage(CompanyRole.Owner)).isFalse()
        assertThat(CompanyRole.Office.canManage(CompanyRole.Office)).isFalse()
        assertThat(CompanyRole.Office.canManage(CompanyRole.Crew)).isTrue()
        assertThat(CompanyRole.Office.assignableRoles).containsExactly(CompanyRole.Lead, CompanyRole.Crew, CompanyRole.Sub)
    }

    @Test fun leadAndCrewManageNobody() {
        for (role in listOf(CompanyRole.Lead, CompanyRole.Crew, CompanyRole.Sub)) {
            assertThat(role.assignableRoles).isEmpty()
            assertThat(CompanyRole.entries.none { role.canManage(it) }).isTrue()
        }
    }

    @Test fun unknownWireValueFallsBackToCrew() {
        assertThat(CompanyRole.fromWire("owner")).isEqualTo(CompanyRole.Owner)
        assertThat(CompanyRole.fromWire("wizard")).isEqualTo(CompanyRole.Crew)
    }

    @Test fun displayNameFallsBackToEmailLocalPart() {
        assertThat(TeamMember("u", null, "sam.rivera@x.com", CompanyRole.Crew, null).displayName).isEqualTo("sam.rivera")
        assertThat(TeamMember("u", "Sam Rivera", "s@x.com", CompanyRole.Crew, null).displayName).isEqualTo("Sam Rivera")
    }
}

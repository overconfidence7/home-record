package com.kurtz.homerecord.fieldlog.log

import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.data.CompanyEntity

/**
 * Who heads a daily log PDF (AC-F33). The company profile lives on the server, so the local company row may not exist
 * (it never does for a company made on the welcome screen); the signed-in membership always knows the company's name,
 * and its phone (plus its email for Owner and Office). The local row's non-blank fields win; the membership fills the
 * rest, and only when it is the job's company.
 */
object LogPdfCompany {
    fun of(jobCompanyId: String, local: CompanyEntity?, membership: CompanyMembership?): CompanyEntity? {
        val m = membership?.takeIf { it.companyId == jobCompanyId } ?: return local
        fun String?.orFrom(other: String?) = this?.takeIf { it.isNotBlank() } ?: other?.takeIf { it.isNotBlank() }
        val base = local ?: CompanyEntity(id = jobCompanyId, name = "", createdAt = 0, updatedAt = 0)
        return base.copy(
            name = base.name.orFrom(m.companyName).orEmpty(),
            phone = base.phone.orFrom(m.phone),
            email = base.email.orFrom(m.email),
        )
    }
}

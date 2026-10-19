package com.kurtz.homerecord.fieldlog.company

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.fieldlog.R

@Composable
fun failureText(failure: ApiFailure): String = stringResource(
    when (failure) {
        ApiFailure.Offline -> R.string.company_err_offline
        ApiFailure.InvalidCode -> R.string.company_err_invalid_code
        ApiFailure.Invalid -> R.string.company_err_invalid
        ApiFailure.Forbidden -> R.string.company_err_forbidden
        ApiFailure.NotFound -> R.string.company_err_not_found
        ApiFailure.Failed -> R.string.company_err_failed
    },
)

@Composable
fun roleLabel(role: CompanyRole): String = stringResource(
    when (role) {
        CompanyRole.Owner -> R.string.role_owner
        CompanyRole.Office -> R.string.role_office
        CompanyRole.Lead -> R.string.role_lead
        CompanyRole.Crew -> R.string.role_crew
        CompanyRole.Sub -> R.string.role_sub
    },
)

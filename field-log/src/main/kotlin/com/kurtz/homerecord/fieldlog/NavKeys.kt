package com.kurtz.homerecord.fieldlog

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object TeamKey : NavKey

@Serializable data object CompanyKey : NavKey

@Serializable data class JobEditKey(val jobId: String? = null) : NavKey

@Serializable data class JobKey(val jobId: String) : NavKey

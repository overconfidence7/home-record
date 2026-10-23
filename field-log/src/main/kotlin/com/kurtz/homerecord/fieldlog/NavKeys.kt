package com.kurtz.homerecord.fieldlog

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object TeamKey : NavKey

@Serializable data object CompanyKey : NavKey

@Serializable data class JobEditKey(val jobId: String? = null) : NavKey

@Serializable data class JobKey(val jobId: String) : NavKey

@Serializable data class JobPlanKey(val homeId: String, val title: String, val floorId: String? = null, val roomId: String? = null, val editShape: Boolean = false) : NavKey

@Serializable data class JobFloorsKey(val homeId: String, val title: String) : NavKey

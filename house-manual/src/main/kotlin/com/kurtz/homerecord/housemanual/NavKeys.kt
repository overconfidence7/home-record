package com.kurtz.homerecord.housemanual

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object HouseKey : NavKey

@Serializable data object FloorsKey : NavKey

@Serializable data class PlanKey(val floorId: String? = null, val roomId: String? = null, val editShape: Boolean = false) : NavKey

@Serializable data class RoomKey(val roomId: String) : NavKey

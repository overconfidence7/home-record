package com.kurtz.homerecord.housemanual.emergency

import com.kurtz.homerecord.core.logic.EmergencyGroup
import com.kurtz.homerecord.core.model.Shutoff

data class EmergencyUiState(
    val loaded: Boolean = false,
    val groups: List<Pair<EmergencyGroup, List<Shutoff>>> = emptyList(),
    val roomNames: Map<String, String> = emptyMap(),
    val zoomedPhotoPath: String? = null,
)

sealed interface EmergencyEvent {
    data class ZoomPhoto(val path: String) : EmergencyEvent
    data object CloseZoom : EmergencyEvent
    data object ManageShutoffs : EmergencyEvent
}

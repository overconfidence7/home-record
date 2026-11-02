package com.kurtz.homerecord.housemodel.overlays

import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer

/** How a kind is drawn by default (AC-C25): a run along the wall or plan, or a single spot. Either shape may be stored. */
enum class OverlayShape { LINE, POINT }

/**
 * What an overlay marks (AC-C25). Stored by [wire] in `overlay.kind`. [layer] is the blueprint and 3D layer it draws on
 * and takes its color from (AC-C15); studs, blocking and other marks belong to no system layer and draw in the neutral
 * structure color, always shown on the wall. [terms] are extra words search matches besides the kind's name (AC-C28).
 */
enum class OverlayKind(val wire: String, val layer: BlueprintLayer?, val shape: OverlayShape, val terms: List<String>) {
    WATER_HOT("water_hot", BlueprintLayer.PLUMBING, OverlayShape.LINE, listOf("water", "supply", "hot", "pipe", "pex")),
    WATER_COLD("water_cold", BlueprintLayer.PLUMBING, OverlayShape.LINE, listOf("water", "supply", "cold", "pipe", "pex")),
    DRAIN("drain", BlueprintLayer.DRAIN_VENT, OverlayShape.LINE, listOf("drain", "waste", "pipe")),
    VENT("vent", BlueprintLayer.DRAIN_VENT, OverlayShape.LINE, listOf("vent", "stack", "pipe")),
    GAS("gas", BlueprintLayer.GAS, OverlayShape.LINE, listOf("gas", "line", "pipe")),
    ELECTRICAL_WIRE("electrical_wire", BlueprintLayer.ELECTRICAL, OverlayShape.LINE, listOf("wire", "cable", "circuit", "romex", "electrical")),
    LV_DATA("lv_data", BlueprintLayer.LOW_VOLTAGE, OverlayShape.LINE, listOf("data", "ethernet", "network", "cat6", "low-voltage")),
    LV_COAX("lv_coax", BlueprintLayer.LOW_VOLTAGE, OverlayShape.LINE, listOf("coax", "cable", "tv", "low-voltage")),
    LV_SPEAKER("lv_speaker", BlueprintLayer.LOW_VOLTAGE, OverlayShape.LINE, listOf("speaker", "audio", "low-voltage")),
    LV_SECURITY("lv_security", BlueprintLayer.LOW_VOLTAGE, OverlayShape.LINE, listOf("security", "alarm", "camera", "low-voltage")),
    LV_DOORBELL("lv_doorbell", BlueprintLayer.LOW_VOLTAGE, OverlayShape.LINE, listOf("doorbell", "chime", "low-voltage")),
    DUCT("duct", BlueprintLayer.HVAC, OverlayShape.LINE, listOf("duct", "hvac", "air")),
    STUD("stud", null, OverlayShape.LINE, listOf("stud", "framing")),
    BLOCKING("blocking", null, OverlayShape.LINE, listOf("blocking", "backing", "framing")),
    JUNCTION_BOX("junction_box", BlueprintLayer.ELECTRICAL, OverlayShape.POINT, listOf("junction", "box", "electrical")),
    OUTLET("outlet", BlueprintLayer.ELECTRICAL, OverlayShape.POINT, listOf("outlet", "receptacle", "plug", "electrical")),
    SWITCH("switch", BlueprintLayer.ELECTRICAL, OverlayShape.POINT, listOf("switch", "electrical")),
    LIGHT("light", BlueprintLayer.ELECTRICAL, OverlayShape.POINT, listOf("light", "fixture", "lamp", "electrical")),
    REGISTER("register", BlueprintLayer.HVAC, OverlayShape.POINT, listOf("register", "vent", "grille", "hvac")),
    VALVE("valve", BlueprintLayer.PLUMBING, OverlayShape.POINT, listOf("valve", "shutoff", "water")),
    CLEANOUT("cleanout", BlueprintLayer.DRAIN_VENT, OverlayShape.POINT, listOf("cleanout", "drain")),
    OTHER("other", null, OverlayShape.POINT, emptyList()),
    ;

    companion object {
        /** The stored kind read back; an unknown value counts as [OTHER] so nothing drawn is lost. */
        fun fromWire(wire: String?): OverlayKind = entries.firstOrNull { it.wire == wire } ?: OTHER

        /** The kinds a [layer] toggle shows (AC-C15). */
        fun onLayer(layer: BlueprintLayer): List<OverlayKind> = entries.filter { it.layer == layer }
    }
}

/** Where the overlay runs (AC-C26). Stored by name in `overlay.depth`. */
enum class OverlayDepth {
    BEHIND_DRYWALL,
    IN_SLAB,
    IN_CEILING,
    IN_ATTIC,
    UNDERGROUND,
    EXPOSED,
    ;

    companion object {
        /** An unknown or damaged value counts as [BEHIND_DRYWALL], the default for a mark on a wall. */
        fun fromStored(value: String?): OverlayDepth = entries.firstOrNull { it.name == value } ?: BEHIND_DRYWALL
    }
}

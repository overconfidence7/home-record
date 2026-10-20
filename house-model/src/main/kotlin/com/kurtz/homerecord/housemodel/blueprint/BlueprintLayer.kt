package com.kurtz.homerecord.housemodel.blueprint

/** The overlay layers a blueprint can show (AC-C15). Items are the markers for installed items. */
enum class BlueprintLayer(val wire: String) {
    ITEMS("items"),
    ELECTRICAL("electrical"),
    PLUMBING("plumbing"),
    HVAC("hvac"),
    LOW_VOLTAGE("low_voltage"),
    GAS("gas"),
    DRAIN_VENT("drain_vent"),
    ;

    companion object {
        fun fromWire(wire: String): BlueprintLayer? = entries.firstOrNull { it.wire == wire }
    }
}

/** Which layers are on. Immutable; [toggled] returns the new set. */
data class LayerSet(val on: Set<BlueprintLayer> = emptySet()) {
    operator fun contains(layer: BlueprintLayer) = layer in on
    fun toggled(layer: BlueprintLayer) = LayerSet(if (layer in on) on - layer else on + layer)

    fun encode(): String = BlueprintLayer.entries.filter { it in on }.joinToString(",") { it.wire }

    companion object {
        fun decode(text: String?): LayerSet =
            LayerSet(text.orEmpty().split(',').mapNotNull { BlueprintLayer.fromWire(it.trim()) }.toSet())
    }
}

/** Layer toggles are remembered per home (AC-C15). */
interface LayerStore {
    fun read(homeId: String): LayerSet
    fun write(homeId: String, layers: LayerSet)
}

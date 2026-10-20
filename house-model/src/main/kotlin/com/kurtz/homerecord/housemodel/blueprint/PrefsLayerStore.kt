package com.kurtz.homerecord.housemodel.blueprint

import android.content.Context

class PrefsLayerStore(context: Context) : LayerStore {
    private val prefs = context.getSharedPreferences("blueprint_layers", Context.MODE_PRIVATE)

    override fun read(homeId: String): LayerSet = LayerSet.decode(prefs.getString(homeId, null))

    override fun write(homeId: String, layers: LayerSet) {
        prefs.edit().putString(homeId, layers.encode()).apply()
    }
}

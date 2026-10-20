package com.kurtz.homerecord.housemodel.plan

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Reads and writes a room's corners as the `[{"x":0,"y":0},...]` JSON stored in `room.polygonJson`. */
object PlanCodec {
    fun encode(points: List<Pt>): String {
        val array = JSONArray()
        points.forEach { array.put(JSONObject().put("x", it.x).put("y", it.y)) }
        return array.toString()
    }

    /** Empty for a room that has no shape yet or whose JSON is damaged; such a room counts as unplaced. */
    fun decode(json: String): List<Pt> = try {
        val array = JSONArray(json)
        val points = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Pt(o.getInt("x"), o.getInt("y"))
        }
        if (points.size >= 3) points else emptyList()
    } catch (_: JSONException) {
        emptyList()
    }
}

package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Shutoff

data class SearchData(
    val breakers: List<Breaker> = emptyList(),
    val breakerRooms: List<BreakerRoom> = emptyList(),
    val endpoints: List<Endpoint> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val shutoffs: List<Shutoff> = emptyList(),
)

sealed interface SearchResult {
    val score: Int

    /** [matchedEndpoints] are the endpoint labels that contributed to the match. */
    data class BreakerHit(val breaker: Breaker, val matchedEndpoints: List<String>, override val score: Int) : SearchResult
    data class EndpointHit(val endpoint: Endpoint, override val score: Int) : SearchResult
    data class EquipmentHit(val equipment: Equipment, override val score: Int) : SearchResult
    data class ShutoffHit(val shutoff: Shutoff, override val score: Int) : SearchResult
}

/**
 * AC-17: case-insensitive, token-based search. Every query token must be a substring of at least
 * one searchable field of the item. Breakers whose own label matches tokens rank first.
 */
object SearchEngine {
    fun tokens(query: String): List<String> =
        query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct()

    fun search(query: String, data: SearchData): List<SearchResult> {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return emptyList()
        val roomName = data.rooms.associate { it.id to it.name.lowercase() }
        val results = mutableListOf<SearchResult>()

        for (b in data.breakers) {
            val eps = data.endpoints.filter { it.breakerId == b.id }
            val rooms = (data.breakerRooms.filter { it.breakerId == b.id }.map { it.roomId } + eps.map { it.roomId })
                .distinct().mapNotNull { roomName[it] }
            val label = b.label.lowercase()
            val epLabels = eps.map { it.label.lowercase() }
            val fields = listOf(label) + epLabels + rooms
            if (!matchesAll(tokens, fields)) continue
            val labelHits = tokens.count { label.contains(it) }
            val matched = eps.filter { e -> tokens.any { e.label.lowercase().contains(it) } }.map { it.label }
            results += SearchResult.BreakerHit(b, matched, 100 + labelHits * 10 + matched.size)
        }
        for (e in data.endpoints.filter { it.breakerId == null }) {
            if (matchesAll(tokens, listOf(e.label.lowercase(), roomName[e.roomId].orEmpty()))) {
                results += SearchResult.EndpointHit(e, 50)
            }
        }
        for (q in data.equipment) {
            val fields = listOfNotNull(q.make, q.model, q.category.searchName(), q.roomId?.let { roomName[it] }).map { it.lowercase() }
            if (matchesAll(tokens, fields)) results += SearchResult.EquipmentHit(q, 40)
        }
        for (s in data.shutoffs) {
            val fields = listOfNotNull(s.kind.searchName(), s.instructions, s.roomId?.let { roomName[it] }).map { it.lowercase() }
            if (matchesAll(tokens, fields)) results += SearchResult.ShutoffHit(s, 30)
        }
        return results.sortedByDescending { it.score }
    }

    private fun matchesAll(tokens: List<String>, fields: List<String>) =
        tokens.all { t -> fields.any { it.contains(t) } }

    private fun Enum<*>.searchName() = name.lowercase().replace('_', ' ')
}

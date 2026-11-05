package com.kurtz.homerecord.core.data

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.LinkConfidence
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import kotlinx.serialization.json.Json

private val slotsJson = Json { ignoreUnknownKeys = true }

fun PanelEntity.toPanel(): Panel = Panel(id, itemId, slotCount, PanelLayout.of(layout), mainAmps, brand, photoPath)

fun BreakerEntity.toBreaker(): Breaker = Breaker(
    id = id, panelId = panelId, slots = decodeSlots(slotsJson), poles = poles, amps = amps,
    type = BreakerType.of(type), label = label, confidence = LinkConfidence.of(confidence), createdAt = createdAt,
)

fun encodeSlots(slots: List<Int>): String = slotsJson.encodeToString(slots.sorted())

/** A broken column reads as no slots, so the breaker shows as unplaced instead of crashing the panel screen. */
fun decodeSlots(json: String): List<Int> = runCatching { slotsJson.decodeFromString<List<Int>>(json) }.getOrDefault(emptyList()).sorted()

/** One link from a breaker to a room or item, with the breaker and the name of the panel item it sits in (AC-C41). */
data class BreakerFeedRow(
    val linkId: String,
    val linkConfidence: String,
    val panelItemId: String,
    val panelName: String,
    val breakerId: String,
    val panelId: String,
    val slotsJson: String,
    val poles: Int,
    val amps: Int?,
    val type: String,
    val label: String,
    val breakerConfidence: String,
    val createdAt: Long,
) {
    fun toBreaker(): Breaker = Breaker(
        id = breakerId, panelId = panelId, slots = decodeSlots(slotsJson), poles = poles, amps = amps,
        type = BreakerType.of(type), label = label, confidence = LinkConfidence.of(breakerConfidence), createdAt = createdAt,
    )
}

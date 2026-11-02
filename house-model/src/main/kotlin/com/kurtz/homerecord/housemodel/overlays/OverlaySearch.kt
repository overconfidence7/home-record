package com.kurtz.homerecord.housemodel.overlays

/**
 * Finds overlays by label and kind (AC-C28). Every word of the query must match: the label (by word prefix), the kind's
 * shown name, or one of the kind's [OverlayKind.terms] ("pipe" finds supply, drain, vent and gas lines). Label matches
 * rank first (a label starting with the query before one containing it), then kind matches; ties stay newest first.
 */
class OverlaySearch(private val kindName: (OverlayKind) -> String) {
    fun search(overlays: List<Overlay>, query: String): List<Overlay> {
        val words = tokens(query)
        if (words.isEmpty()) return overlays.sortedByDescending { it.updatedAt }
        return overlays.mapNotNull { overlay -> score(overlay, query.trim().lowercase(), words)?.let { overlay to it } }
            .sortedWith(compareByDescending<Pair<Overlay, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            .map { it.first }
    }

    private fun score(overlay: Overlay, phrase: String, words: List<String>): Int? {
        val label = overlay.label.lowercase()
        val labelWords = tokens(overlay.label)
        val kindWords = (tokens(kindName(overlay.kind)) + overlay.kind.terms.flatMap(::tokens)).toSet()
        var labelHits = 0
        for (word in words) {
            val inLabel = labelWords.any { it.startsWith(word) }
            val inKind = kindWords.any { it.startsWith(word) }
            if (!inLabel && !inKind) return null
            if (inLabel) labelHits++
        }
        return when {
            label.startsWith(phrase) -> 3
            labelHits == words.size -> 2
            labelHits > 0 -> 1
            else -> 0
        } * 10 + labelHits
    }

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
}

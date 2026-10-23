package com.kurtz.homerecord.core.model

enum class SearchKind { ITEM, ROOM, OVERLAY, SHUTOFF, BREAKER, DOCUMENT, CONTACT }

/** One searchable thing. [fields] are ordered most to least important (name first); [id] opens it. */
data class SearchDocument(
    val kind: SearchKind,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val fields: List<String> = listOf(title),
)

data class SearchHit(val document: SearchDocument, val score: Int)

/** Ranks documents against a query: exact > prefix > word-prefix > substring, with every query token required. */
object SearchEngine {
    fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    fun search(documents: List<SearchDocument>, query: String, limit: Int = 50): List<SearchHit> {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return documents.mapNotNull { doc -> score(doc, tokens)?.let { SearchHit(doc, it) } }
            .sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.document.title.lowercase() })
            .take(limit)
    }

    private fun score(doc: SearchDocument, tokens: List<String>): Int? {
        val fields = doc.fields.map(::normalize)
        var total = 0
        for (token in tokens) {
            var best = 0
            fields.forEachIndexed { index, field ->
                val weight = (fields.size - index).coerceAtLeast(1)
                val s = tokenScore(field, token) * 10 + weight
                if (tokenScore(field, token) > 0 && s > best) best = s
            }
            if (best == 0) return null
            total += best
        }
        return total
    }

    private fun tokenScore(field: String, token: String): Int = when {
        field == token -> 4
        field.startsWith(token) -> 3
        field.split(' ').any { it.startsWith(token) } -> 2
        field.contains(token) -> 1
        else -> 0
    }
}

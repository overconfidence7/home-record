package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelKind
import com.kurtz.homerecord.core.model.TandemPolicy

/** Raw text from the panel profile form (AC-101). Blank optional fields are valid. */
data class PanelProfileInput(
    val kind: PanelKind = PanelKind.MAIN,
    val ratingAmps: String = "",
    val brand: String = "",
    val model: String = "",
    val maxCircuits: String = "",
    val tandemPolicy: TandemPolicy = TandemPolicy.UNKNOWN,
    val hasMainBreaker: Boolean = true,
    val yearInstalled: String = "",
    val feedBreakerId: String? = null,
) {
    companion object {
        fun from(panel: Panel) = PanelProfileInput(
            kind = panel.kind,
            ratingAmps = panel.ratingAmps?.toString().orEmpty(),
            brand = panel.brand.orEmpty(),
            model = panel.model.orEmpty(),
            maxCircuits = panel.maxCircuits?.toString().orEmpty(),
            tandemPolicy = panel.tandemPolicy,
            hasMainBreaker = panel.hasMainBreaker,
            yearInstalled = panel.yearInstalled?.toString().orEmpty(),
            feedBreakerId = panel.feedBreakerId,
        )
    }
}

enum class PanelProfileError { RATING, MAX_CIRCUITS, YEAR }

/** The parsed profile fields, ready to copy onto a [Panel]. */
data class PanelProfile(
    val kind: PanelKind,
    val ratingAmps: Int?,
    val brand: String?,
    val model: String?,
    val maxCircuits: Int?,
    val tandemPolicy: TandemPolicy,
    val hasMainBreaker: Boolean,
    val yearInstalled: Int?,
    val feedBreakerId: String?,
) {
    fun applyTo(panel: Panel) = panel.copy(
        kind = kind, ratingAmps = ratingAmps, brand = brand, model = model, maxCircuits = maxCircuits,
        tandemPolicy = tandemPolicy, hasMainBreaker = hasMainBreaker, yearInstalled = yearInstalled,
        // Only a sub-panel has a feed breaker.
        feedBreakerId = if (kind == PanelKind.SUB) feedBreakerId else null,
    )
}

sealed interface PanelProfileResult {
    data class Valid(val profile: PanelProfile) : PanelProfileResult
    data class Invalid(val errors: Set<PanelProfileError>) : PanelProfileResult
}

/** AC-101 validation and AC-102 feed-breaker helpers. Pure. */
object PanelProfileRule {
    val RATING_CHOICES = listOf(60, 100, 125, 150, 200, 225, 400)

    val BRAND_SUGGESTIONS = listOf(
        "Square D QO", "Square D Homeline", "Eaton BR", "Eaton CH", "Siemens", "GE", "Leviton",
        "Federal Pacific", "Zinsco", "Challenger", "Other",
    )

    private const val MAX_RATING = 1200
    private const val MAX_CIRCUITS = 200
    private const val MIN_YEAR = 1880

    fun validate(input: PanelProfileInput, currentYear: Int): PanelProfileResult {
        val errors = mutableSetOf<PanelProfileError>()
        val rating = optionalInt(input.ratingAmps, 1..MAX_RATING) { errors += PanelProfileError.RATING }
        val max = optionalInt(input.maxCircuits, 1..MAX_CIRCUITS) { errors += PanelProfileError.MAX_CIRCUITS }
        val year = optionalInt(input.yearInstalled, MIN_YEAR..currentYear) { errors += PanelProfileError.YEAR }
        if (errors.isNotEmpty()) return PanelProfileResult.Invalid(errors)
        return PanelProfileResult.Valid(
            PanelProfile(
                kind = input.kind,
                ratingAmps = rating,
                brand = input.brand.trim().ifEmpty { null },
                model = input.model.trim().ifEmpty { null },
                maxCircuits = max,
                tandemPolicy = input.tandemPolicy,
                hasMainBreaker = input.hasMainBreaker,
                yearInstalled = year,
                feedBreakerId = input.feedBreakerId,
            ),
        )
    }

    private inline fun optionalInt(text: String, range: IntRange, onBad: () -> Unit): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val n = t.toIntOrNull()
        if (n == null || n !in range) {
            onBad()
            return null
        }
        return n
    }

    /** AC-102: breakers from panels other than [panelId] that can feed a sub-panel. Main breakers first. */
    fun feedCandidates(panelId: String, breakers: List<Breaker>): List<Breaker> =
        breakers.filter { it.panelId != panelId && it.tandemIndex == null }
            .sortedWith(compareBy<Breaker>({ !it.isMain }, { it.panelId }, { it.slots.minOrNull() ?: 0 }))

    /** AC-102: breaker id -> name of the sub-panel it feeds. */
    fun feedsByBreaker(panels: List<Panel>): Map<String, String> =
        panels.filter { it.kind == PanelKind.SUB && it.feedBreakerId != null }
            .associate { it.feedBreakerId.orEmpty() to it.name }
}

/** AC-103: only one main breaker per panel. */
object MainBreakerRule {
    /** Breakers whose isMain flag must be cleared when [newMainId] becomes the panel's main. */
    fun toClear(breakers: List<Breaker>, newMainId: String): List<Breaker> =
        breakers.filter { it.isMain && it.id != newMainId }

    /** The panel's main breaker, if one is flagged. */
    fun mainOf(breakers: List<Breaker>): Breaker? = breakers.firstOrNull { it.isMain }
}

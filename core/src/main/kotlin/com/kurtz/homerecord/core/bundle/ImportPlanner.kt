package com.kurtz.homerecord.core.bundle

/**
 * Plans how a bundle merges into a home on this phone (AC-C47).
 *
 * For each imported record:
 * - no local record with that id → [ImportAction.Add] (a deleted one is skipped);
 * - same content → [ImportAction.Same] (planning the same bundle twice changes nothing);
 * - only the imported side changed since the last import ([base]) → [ImportAction.Update];
 * - only the local side changed → [ImportAction.KeepLocal];
 * - both changed, or there is no base to tell → [ImportAction.Conflict], for the user to pick
 *   Keep mine or Use imported. User-entered data is never overwritten without that choice.
 *
 * [base] is each record's `updatedAt` as last imported here (type → id → updatedAt), from the
 * import ledger; it is empty for a first import. Under `kept:<type>` it also says which records this phone kept
 * its own version of (1) at that import: those stay "changed here", so a later crew edit to them is a conflict
 * again instead of an update over the homeowner's version (r12:import-keep-mine-lost).
 */
object ImportPlanner {
    fun plan(
        bundle: HomeBundle,
        local: Map<String, List<BundleRecord>>,
        base: Map<String, Map<String, Long>> = emptyMap(),
    ): ImportPlan {
        val steps = bundle.records.flatMap { (type, imported) ->
            val mine = local[type].orEmpty().associateBy { it.id }
            val known = base[type].orEmpty()
            val kept = base[KEPT + type].orEmpty()
            imported.distinctBy { it.id }.mapNotNull { theirs ->
                val action = decide(mine[theirs.id], theirs, known[theirs.id], kept[theirs.id] == 1L) ?: return@mapNotNull null
                ImportStep(type, theirs.id, action, theirs, mine[theirs.id])
            }
        }
        return ImportPlan(steps)
    }

    /** Ledger type prefix for the records this phone kept its own version of. */
    const val KEPT = "kept:"

    private fun decide(mine: BundleRecord?, theirs: BundleRecord, base: Long?, keptMine: Boolean): ImportAction? {
        if (mine == null) return if (theirs.deleted) null else ImportAction.Add
        if (mine.content() == theirs.content()) return ImportAction.Same
        val theyChanged = base == null || theirs.updatedAt > base
        val iChanged = base == null || keptMine || mine.updatedAt > base
        return when {
            theyChanged && !iChanged -> ImportAction.Update
            !theyChanged -> ImportAction.KeepLocal
            else -> ImportAction.Conflict
        }
    }
}

enum class ImportAction { Add, Update, Same, KeepLocal, Conflict }

enum class ConflictChoice { KeepMine, UseImported }

data class ImportStep(
    val type: String,
    val id: String,
    val action: ImportAction,
    val imported: BundleRecord,
    val local: BundleRecord?,
)

data class ImportPlan(val steps: List<ImportStep>) {
    val conflicts: List<ImportStep> get() = steps.filter { it.action == ImportAction.Conflict }

    /** New records per entity type, for the preview ("12 rooms, 4 items"). */
    fun added(type: String): Int = steps.count { it.type == type && it.action == ImportAction.Add }

    fun updated(type: String): Int = steps.count { it.type == type && it.action == ImportAction.Update }

    /** True when importing would change nothing on this phone. */
    val nothingToDo: Boolean get() = steps.all { it.action == ImportAction.Same || it.action == ImportAction.KeepLocal }

    /**
     * The records to write, with the user's [choices] for conflicts (type to id to choice).
     * A conflict with no choice keeps the local record: nothing is overwritten by default.
     */
    fun writes(choices: Map<Pair<String, String>, ConflictChoice> = emptyMap()): List<ImportStep> = steps.filter {
        when (it.action) {
            ImportAction.Add, ImportAction.Update -> true
            ImportAction.Conflict -> choices[it.type to it.id] == ConflictChoice.UseImported
            ImportAction.Same, ImportAction.KeepLocal -> false
        }
    }

    /**
     * What the ledger records after the import with the user's [choices]: every imported record's `updatedAt`, so the
     * next import knows the base, and under `kept:<type>` whether this phone kept its own version (1) or now holds the
     * imported one (0).
     */
    fun ledger(choices: Map<Pair<String, String>, ConflictChoice> = emptyMap()): Map<String, Map<String, Long>> {
        val byType = steps.groupBy { it.type }
        val seen = byType.mapValues { (_, list) -> list.associate { it.id to it.imported.updatedAt } }
        val kept = byType.entries.associate { (type, list) ->
            ImportPlanner.KEPT + type to list.associate { step ->
                val keptMine = step.action == ImportAction.KeepLocal ||
                    (step.action == ImportAction.Conflict && choices[step.type to step.id] != ConflictChoice.UseImported)
                step.id to if (keptMine) 1L else 0L
            }
        }
        return seen + kept
    }
}

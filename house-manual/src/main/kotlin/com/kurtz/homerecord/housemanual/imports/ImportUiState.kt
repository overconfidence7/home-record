package com.kurtz.homerecord.housemanual.imports

import com.kurtz.homerecord.core.imports.ConflictChoice
import com.kurtz.homerecord.core.imports.ImportConflict
import com.kurtz.homerecord.core.imports.ImportEntityType
import com.kurtz.homerecord.core.imports.ImportResult

enum class ImportPhase { PICK, READING, PREVIEW, DONE, ERROR, NEEDS_PASSPHRASE }

data class ImportUiState(
    val phase: ImportPhase = ImportPhase.PICK,
    val counts: List<Pair<ImportEntityType, Int>> = emptyList(),
    val conflicts: List<ImportConflict> = emptyList(),
    /** Missing = keep mine. */
    val choices: Map<String, ConflictChoice> = emptyMap(),
    val alreadyPresent: Int = 0,
    val result: ImportResult? = null,
    val applying: Boolean = false,
    val wrongPassphrase: Boolean = false,
    /** No local Home yet: importing recreates the bundle's Home (restore onto a fresh install). */
    val restoresHome: Boolean = false,
) {
    val nothingToImport: Boolean get() = counts.isEmpty() && conflicts.isEmpty() && !restoresHome
    fun choiceFor(id: String): ConflictChoice = choices[id] ?: ConflictChoice.KEEP_MINE
}

sealed interface ImportEvent {
    data class FileChosen(val uri: String) : ImportEvent
    data class Choose(val id: String, val choice: ConflictChoice) : ImportEvent
    data class PassphraseEntered(val passphrase: String) : ImportEvent
    data object Confirm : ImportEvent
    data object Reset : ImportEvent
}

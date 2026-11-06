package com.kurtz.homerecord.housemanual.importing

/** What kind of records a count row or a conflict is about, worded by the screen. */
enum class ImportKind(val type: String) {
    Floors("floor"), Rooms("room"), WallPhotos("wall_photo"), Overlays("overlay"), Items("item"),
    Documents("document"), ChangeOrders("change_order"), Other("");

    companion object {
        fun of(type: String): ImportKind = entries.firstOrNull { it.type == type && it != Other } ?: Other
    }
}

/** Why the file could not be imported (docs/voice.md words each one). */
enum class ImportError {
    NewerVersion, NotABundle, TooLarge, Unreadable, Failed,

    /** Restore only (AC-C48): the passphrase does not open the backup. */
    WrongPassphrase,

    /** Restore only: the backup opened but is damaged or cut short. */
    Damaged,

    /** Restore only: the picked file is not a `.hrbackup`. */
    NotABackup,
}

data class ImportCount(val kind: ImportKind, val count: Int)

/** A record changed both here and by the crew: Keep mine unless [useImported] (AC-C47). */
data class ImportConflict(val type: String, val id: String, val kind: ImportKind, val name: String, val useImported: Boolean = false)

/** HM-Import: preview of a Field Log handoff (`.homerecord`), conflicts, Import (AC-H17). */
data class ImportUiState(
    val loading: Boolean = true,
    val error: ImportError? = null,
    /** Who sent it, from the bundle's home name or address. */
    val homeName: String = "",
    val counts: List<ImportCount> = emptyList(),
    val conflicts: List<ImportConflict> = emptyList(),
    val importing: Boolean = false,
    val done: Boolean = false,
    /** A backup being restored (AC-H14) rather than a contractor's handoff: the screen words it as a restore. */
    val restore: Boolean = false,
) {
    /** Nothing in the file is new or changed for this phone. */
    val nothingNew: Boolean get() = !loading && error == null && counts.isEmpty() && conflicts.isEmpty()
}

sealed interface ImportEvent {
    data object Back : ImportEvent
    data class Choose(val type: String, val id: String, val useImported: Boolean) : ImportEvent
    data object Import : ImportEvent
    /** After the import: close and show the house. */
    data object Finish : ImportEvent
}

package com.kurtz.homerecord.housemodel.ui

import androidx.lifecycle.SavedStateHandle
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.overlays.OverlayDepth
import com.kurtz.homerecord.housemodel.overlays.OverlayDraft
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoints
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * The overlay form's logic (AC-C26), shared by the wall editor and the plan editor: the fields, the kind, depth, photo and
 * item sheets, the photo from the camera or the gallery (a fresh photo is deleted on Close or when replaced), and saving
 * the form in [savedState] so it survives process death. The owner keeps the form in its UI state through [show] and
 * does the save itself with [draftForSave] and [saveFailed].
 */
internal class OverlayFormController(
    private val savedState: SavedStateHandle,
    private val media: MediaFiles?,
    private val scope: CoroutineScope,
    private val current: () -> OverlayForm?,
    private val show: (OverlayForm?) -> Unit,
    private val launchPhoto: (OverlayPhotoLaunch) -> Unit,
) {
    /** The form open before the process died, if any. */
    fun restore(): OverlayForm? = savedState.get<String>(KEY_FORM)?.let(::decodeForm)

    fun open(form: OverlayForm?) {
        savedState[KEY_FORM] = form?.let(::encodeForm)
        show(form)
    }

    /** Handles the form's own events; false for any other event. */
    fun onEvent(event: WallOverlaysEvent): Boolean {
        when (event) {
            is WallOverlaysEvent.OpenSheet -> edit(dirty = false) { it.copy(sheet = event.sheet) }
            is WallOverlaysEvent.SetKind -> edit { it.copy(kind = event.kind, sheet = null) }
            is WallOverlaysEvent.SetLabel -> edit { it.copy(label = event.label) }
            is WallOverlaysEvent.SetNote -> edit { it.copy(note = event.note) }
            is WallOverlaysEvent.SetDepth -> edit { it.copy(depth = event.depth, sheet = null) }
            is WallOverlaysEvent.SetFormStage -> edit { it.copy(stage = event.stage) }
            WallOverlaysEvent.CloseForm -> {
                current()?.takeIf { it.photoIsNew }?.photoPath?.let { media?.discard(it) }
                open(null)
            }
            WallOverlaysEvent.TakePhoto -> {
                val files = media ?: return true
                val target = files.newCaptureTarget()
                savedState[KEY_PENDING] = target.path
                edit(dirty = false) { it.copy(sheet = null) }
                launchPhoto(OverlayPhotoLaunch(target))
            }
            WallOverlaysEvent.ChoosePhoto -> {
                if (media == null) return true
                savedState[KEY_PENDING] = null
                edit(dirty = false) { it.copy(sheet = null) }
                launchPhoto(OverlayPhotoLaunch(null))
            }
            is WallOverlaysEvent.CameraResult -> {
                val path = savedState.get<String>(KEY_PENDING) ?: return true
                savedState[KEY_PENDING] = null
                val files = media ?: return true
                if (event.ok && files.exists(path) && current() != null) setPhoto(path) else files.discard(path)
            }
            is WallOverlaysEvent.GalleryResult -> {
                val uri = event.uri ?: return true
                val files = media ?: return true
                scope.launch {
                    val path = files.import(uri) ?: return@launch
                    if (current() != null) setPhoto(path) else files.discard(path)
                }
            }
            WallOverlaysEvent.RemovePhoto -> {
                val form = current() ?: return true
                if (form.photoIsNew) form.photoPath?.let { media?.discard(it) }
                edit { it.copy(photoPath = null, photoIsNew = false) }
            }
            is WallOverlaysEvent.SetItemQuery -> edit(dirty = false) { it.copy(itemQuery = event.query) }
            is WallOverlaysEvent.SetItem -> edit { it.copy(itemId = event.itemId, itemQuery = "", sheet = null) }
            else -> return false
        }
        return true
    }

    /**
     * The draft to save and the overlay it edits (null for a new one), marking the form as saving; null when nothing can
     * be saved yet (no form, already saving, or no kind, which makes the kind field show its error).
     */
    fun draftForSave(): Pair<String?, OverlayDraft>? {
        val form = current() ?: return null
        val kind = form.kind
        if (kind == null) {
            edit(dirty = false) { it.copy(triedSave = true) }
            return null
        }
        if (form.saving) return null
        edit(dirty = false) { it.copy(saving = true) }
        return form.editingId to OverlayDraft(kind, form.points, form.stage, form.label, form.note, form.depth, form.photoPath, form.itemId)
    }

    /** The save did not go through: the form stays open with Save on again. */
    fun saveFailed() = edit(dirty = false) { it.copy(saving = false) }

    /** A new photo on the open form; a fresh one it replaces is deleted, a saved one stays until the overlay is saved. */
    private fun setPhoto(path: String) {
        val form = current() ?: return
        if (form.photoIsNew && form.photoPath != path) form.photoPath?.let { media?.discard(it) }
        edit { it.copy(photoPath = path, photoIsNew = true) }
    }

    private fun edit(dirty: Boolean = true, change: (OverlayForm) -> OverlayForm) {
        val form = current() ?: return
        open(change(form).let { if (dirty) it.copy(dirty = true) else it })
    }

    private companion object {
        const val KEY_FORM = "form"
        const val KEY_PENDING = "pendingPhoto"

        fun encodeForm(form: OverlayForm): String = JSONObject()
            .put("id", form.editingId ?: "")
            .put("points", OverlayPoints.encode(form.points))
            .put("kind", form.kind?.wire ?: "")
            .put("label", form.label)
            .put("note", form.note)
            .put("depth", form.depth.name)
            .put("stage", form.stage.name)
            .put("photo", form.photoPath ?: "")
            .put("photoNew", form.photoIsNew)
            .put("item", form.itemId ?: "")
            .put("query", form.itemQuery)
            .put("sheet", form.sheet?.name ?: "")
            .put("dirty", form.dirty)
            .toString()

        fun decodeForm(json: String): OverlayForm? = try {
            val o = JSONObject(json)
            OverlayForm(
                editingId = o.optString("id").ifEmpty { null },
                points = OverlayPoints.decode(o.optString("points")),
                kind = o.optString("kind").ifEmpty { null }?.let(OverlayKind::fromWire),
                label = o.optString("label"),
                note = o.optString("note"),
                depth = OverlayDepth.fromStored(o.optString("depth")),
                stage = WallStage.fromStored(o.optString("stage")),
                photoPath = o.optString("photo").ifEmpty { null },
                photoIsNew = o.optBoolean("photoNew"),
                itemId = o.optString("item").ifEmpty { null },
                itemQuery = o.optString("query"),
                sheet = OverlayFormSheet.entries.firstOrNull { it.name == o.optString("sheet") },
                dirty = o.optBoolean("dirty"),
            )
        } catch (_: JSONException) {
            null
        }
    }
}

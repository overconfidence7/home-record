package com.kurtz.homerecord.fieldlog.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.PhotoDao
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.data.RoomDao
import com.kurtz.homerecord.core.photo.AnnotationCodec
import com.kurtz.homerecord.core.photo.GalleryCell
import com.kurtz.homerecord.core.photo.OnPhone
import com.kurtz.homerecord.core.photo.PhotoGallery
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

class PhotosDeps(
    val jobId: String,
    val photos: PhotoDao,
    val jobs: JobDao,
    val rooms: RoomDao,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** The paths among these whose bytes are not on this phone ([com.kurtz.homerecord.core.media.MediaDownloads.missing]). */
    val missing: suspend (Collection<String?>) -> Set<String?> = { emptySet() },
    /** Fetches a photo pulled from another phone by its R2 key; the row's path is set when it lands. */
    val download: suspend (String) -> OnPhone = { OnPhone.Ready },
)

/**
 * The job's Photos tab: everything shot on the job, grouped by day with before/after pairs side by side (AC-F24, AC-F25).
 * A photo whose bytes are not here (taken on another phone, AC-F55) shows a placeholder tile and downloads in the
 * background; the tile fills in when the row's path is set.
 */
class PhotosViewModel(private val d: PhotosDeps) : ViewModel() {
    /** Photos with a download running; one that fails leaves the set so the next change to the list tries it again. */
    private val fetching = mutableSetOf<String>()

    private val withMissing = d.photos.observeForJob(d.jobId).map { rows ->
        val gone = d.missing(rows.map { it.path })
        rows.filter { it.path in gone }.forEach(::fetch)
        rows to rows.filter { it.path in gone }.mapTo(mutableSetOf()) { it.id }
    }

    val state: StateFlow<PhotosUiState> = combine(withMissing, roomNames()) { (photos, notHere), names ->
        val days = PhotoGallery.build(photos, d.zone).map { day ->
            PhotoDayUi(
                day.day,
                day.cells.map { cell ->
                    when (cell) {
                        is GalleryCell.Single -> PhotoCell.Single(tile(cell.photo, names, notHere))
                        is GalleryCell.Pair -> PhotoCell.Pair(tile(cell.before, names, notHere), tile(cell.after, names, notHere))
                    }
                },
            )
        }
        PhotosUiState(loading = false, days = days, count = photos.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotosUiState())

    private fun roomNames() = flow {
        val homeId = d.jobs.get(d.jobId)?.homeId
        if (homeId == null) emit(emptyMap()) else d.rooms.observeForHome(homeId).collect { rows -> emit(rows.associate { it.id to it.name }) }
    }

    private fun fetch(p: PhotoEntity) {
        if (!fetching.add(p.id)) return
        viewModelScope.launch {
            if (d.download(p.id) != OnPhone.Ready) fetching.remove(p.id)
        }
    }

    private fun tile(p: PhotoEntity, names: Map<String, String>, notHere: Set<String>) = PhotoTile(
        id = p.id,
        imagePath = if (p.id in notHere) "" else p.thumbPath ?: p.path,
        roomName = p.roomId?.let { names[it] },
        phase = p.stage,
        caption = p.caption?.takeIf { it.isNotBlank() },
        annotated = AnnotationCodec.decode(p.annotationsJson).isNotEmpty(),
    )
}

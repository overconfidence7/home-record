package com.kurtz.homerecord.fieldlog.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.PhotoDao
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.data.RoomDao
import com.kurtz.homerecord.core.photo.AnnotationCodec
import com.kurtz.homerecord.core.photo.GalleryCell
import com.kurtz.homerecord.core.photo.PhotoGallery
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

class PhotosDeps(
    val jobId: String,
    val photos: PhotoDao,
    val jobs: JobDao,
    val rooms: RoomDao,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** The job's Photos tab: everything shot on the job, grouped by day with before/after pairs side by side (AC-F24, AC-F25). */
class PhotosViewModel(private val d: PhotosDeps) : ViewModel() {
    val state: StateFlow<PhotosUiState> = combine(d.photos.observeForJob(d.jobId), roomNames()) { photos, names ->
        val days = PhotoGallery.build(photos, d.zone).map { day ->
            PhotoDayUi(
                day.day,
                day.cells.map { cell ->
                    when (cell) {
                        is GalleryCell.Single -> PhotoCell.Single(tile(cell.photo, names))
                        is GalleryCell.Pair -> PhotoCell.Pair(tile(cell.before, names), tile(cell.after, names))
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

    private fun tile(p: PhotoEntity, names: Map<String, String>) = PhotoTile(
        id = p.id,
        imagePath = p.thumbPath ?: p.path,
        roomName = p.roomId?.let { names[it] },
        phase = p.stage,
        caption = p.caption?.takeIf { it.isNotBlank() },
        annotated = AnnotationCodec.decode(p.annotationsJson).isNotEmpty(),
    )
}

package com.kurtz.homerecord.fieldlog.job

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.DayGroup
import com.kurtz.homerecord.core.logic.EntryGrouper
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Job
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.core.repo.LogEntryRepository
import com.kurtz.homerecord.fieldlog.playback.AudioPlayer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class JobUiState(
    val loaded: Boolean = false,
    val job: Job? = null,
    val days: List<DayGroup> = emptyList(),
    val playingId: String? = null,
)

class JobViewModel(
    jobs: JobRepository,
    entries: LogEntryRepository,
    private val media: MediaFileStore,
    private val player: AudioPlayer,
    jobId: String,
) : ViewModel() {
    private val playing = MutableStateFlow<String?>(null)

    val state: StateFlow<JobUiState> = combine(jobs.observeJob(jobId), entries.observeForJob(jobId), playing) { job, list, playingId ->
        JobUiState(loaded = true, job = job, days = EntryGrouper.group(list), playingId = playingId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobUiState())

    fun file(path: String?): File? = path?.let { media.resolve(it) }

    /** Tap to play a voice entry; tapping the playing one stops it. */
    fun togglePlay(entryId: String, mediaPath: String?) {
        if (playing.value == entryId) {
            player.stop()
            playing.value = null
            return
        }
        val f = file(mediaPath)?.takeIf { it.isFile }
        if (f == null) {
            playing.value = null
            return
        }
        playing.value = if (player.play(f) { playing.value = null }) entryId else null
    }

    override fun onCleared() = player.stop()
}

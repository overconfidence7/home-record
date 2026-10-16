package com.kurtz.homerecord.fieldlog

import android.content.Context
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.EquipmentRepository
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.core.repo.LogEntryRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import com.kurtz.homerecord.core.repo.ShutoffRepository

/** Manual DI: one container per process, created in [FieldLogApp.onCreate]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: HomeRecordDatabase by lazy { HomeRecordDatabase.open(appContext) }
    val mediaStore = MediaFileStore(appContext.filesDir)
    val homeRepository by lazy { HomeRepository(database, Source.FIELD_LOG) }
    val jobRepository by lazy { JobRepository(database, Source.FIELD_LOG) }
    val logEntryRepository by lazy { LogEntryRepository(database, Source.FIELD_LOG) }
    val shutterKeys = com.kurtz.homerecord.fieldlog.capture.ShutterKeyRouter()
    val panelRepository by lazy { PanelRepository(database, mediaStore, Source.FIELD_LOG) }
    val shutoffRepository by lazy { ShutoffRepository(database, mediaStore, Source.FIELD_LOG) }
    val equipmentRepository by lazy { EquipmentRepository(database, mediaStore, Source.FIELD_LOG) }
}

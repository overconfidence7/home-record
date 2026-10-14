package com.kurtz.homerecord.housemanual

import android.content.Context
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.EquipmentRepository
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import com.kurtz.homerecord.core.repo.ShutoffRepository

/** Manual DI: one container per process, created in [HouseManualApp.onCreate]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: HomeRecordDatabase by lazy { HomeRecordDatabase.open(appContext) }
    val mediaStore = MediaFileStore(appContext.filesDir)
    val homeRepository by lazy { HomeRepository(database, Source.HOUSE_MANUAL) }
    val panelRepository by lazy { PanelRepository(database, mediaStore, Source.HOUSE_MANUAL) }
    val shutoffRepository by lazy { ShutoffRepository(database, mediaStore, Source.HOUSE_MANUAL) }
    val equipmentRepository by lazy { EquipmentRepository(database, mediaStore, Source.HOUSE_MANUAL) }
}

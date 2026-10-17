package com.kurtz.homerecord.housemanual

import android.content.Context
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.mapping.AlarmPlayer
import com.kurtz.homerecord.core.mapping.ChargingMonitor
import com.kurtz.homerecord.housemanual.mapping.AndroidAlarmPlayer
import com.kurtz.homerecord.housemanual.mapping.AndroidChargingMonitor
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.ocr.TextRecognizer
import com.kurtz.homerecord.housemanual.equipment.MlKitTextRecognizer
import com.kurtz.homerecord.core.imports.ImportApplier
import com.kurtz.homerecord.core.repo.EquipmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
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
    val chargingMonitor: ChargingMonitor by lazy { AndroidChargingMonitor(appContext) }
    val alarmPlayer: AlarmPlayer by lazy { AndroidAlarmPlayer(appContext) }
    val textRecognizer: TextRecognizer by lazy { MlKitTextRecognizer(appContext) }
    val equipmentRepository by lazy { EquipmentRepository(database, mediaStore, Source.HOUSE_MANUAL) }
    val importApplier by lazy { ImportApplier(database, mediaStore) }

    /** A `.homerecord` URI handed to us by ACTION_VIEW, waiting for the nav host to open HM-Import (AC-75). */
    val pendingImportUri = MutableStateFlow<String?>(null)
}

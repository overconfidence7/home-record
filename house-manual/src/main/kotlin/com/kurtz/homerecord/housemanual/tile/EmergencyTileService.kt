package com.kurtz.homerecord.housemanual.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kurtz.homerecord.housemanual.HouseManualApp
import com.kurtz.homerecord.housemanual.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** AC-32..AC-34: "House emergency" Quick Settings tile that opens HM-Emergency. */
class EmergencyTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val container = (application as HouseManualApp).container
            val home = container.homeRepository.getHome()
            val count = if (home == null) 0 else container.shutoffRepository.observeShutoffs(home.id).first().size
            val tile = qsTile ?: return@launch
            tile.state = if (TileStateChooser.isActive(count)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // Over the lock screen this prompts to unlock first, then opens the screen.
        if (isLocked) unlockAndRun { openEmergency() } else openEmergency()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openEmergency() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_EMERGENCY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

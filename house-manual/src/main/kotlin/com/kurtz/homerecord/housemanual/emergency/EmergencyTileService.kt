package com.kurtz.homerecord.housemanual.emergency

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kurtz.homerecord.housemanual.R

/**
 * Quick settings tile "Emergency" (AC-H11): opens HM-Emergency. From the lock screen it asks for the unlock first, then
 * opens, since the shutoff page is the homeowner's own data.
 */
class EmergencyTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_emergency)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::open) else open()
    }

    // The Intent overload runs only below API 34, where it is the only one there is; 34+ uses the PendingIntent one.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open() {
        val intent = EmergencyEntry.intent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

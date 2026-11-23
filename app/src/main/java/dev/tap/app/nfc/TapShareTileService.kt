package dev.tap.app.nfc

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Android Quick Settings (Control Center) Tile for on-demand TAP Beam.
 *
 * Allows users to pull down their control center / notification shade when meeting someone
 * and toggle NFC Contact Card Beaming / Physical Tap to Share on-demand.
 * Automatically deactivates after 5 minutes so full-time background HCE never runs
 * or interferes with contactless payments.
 */
@RequiresApi(Build.VERSION_CODES.N)
class TapShareTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val repo = NfcSettingsRepository.getInstance(this)
        val currentlyActive = repo.isBackgroundSharingEnabled()
        val newState = !currentlyActive

        if (newState) {
            repo.activateBeam(durationMinutes = 5)
        } else {
            repo.deactivateBeam()
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val repo = NfcSettingsRepository.getInstance(this)
        val isActive = repo.isBackgroundSharingEnabled()

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "TAP Beam"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "Active (5m)" else "Off"
        }
        tile.updateTile()
    }
}

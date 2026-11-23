package dev.tap.app.nfc

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.TileService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manages persistent configuration for TAP's NFC Host Card Emulation (HCE),
 * Quick Settings Control Center tile, and on-demand bidirectional contact beaming.
 *
 * Defaults to OFF (dormant) to eliminate OS contactless payment / NFC embed collisions.
 * When enabled (via Quick Settings tile or in-app switch), it arms for a dedicated
 * 5-minute sharing window and automatically reverts to dormant to protect wallet routing.
 */
class NfcSettingsRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoTimeoutJob: Job? = null

    // Default to FALSE (dormant) so full-time background HCE never clashes with Google Wallet / Samsung Pay
    private val _isBackgroundSharingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BACKGROUND_SHARING, false)
    )
    val isBackgroundSharingEnabled: StateFlow<Boolean> = _isBackgroundSharingEnabled.asStateFlow()

    // Ephemeral unique rendezvous ID for simultaneous bidirectional contact card exchange
    var currentBeamRendezvousId: String = generateRendezvousId()
        private set

    fun isBackgroundSharingEnabled(): Boolean {
        return prefs.getBoolean(KEY_BACKGROUND_SHARING, false)
    }

    fun setBackgroundSharingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_SHARING, enabled).apply()
        _isBackgroundSharingEnabled.value = enabled
        if (enabled) {
            currentBeamRendezvousId = generateRendezvousId()
        }
        notifyTileStateChanged()
    }

    /**
     * Activates TAP Beam on-demand for a set duration (default 5 minutes),
     * automatically disabling it when the window expires to prevent unwanted collisions.
     */
    fun activateBeam(durationMinutes: Int = 5) {
        autoTimeoutJob?.cancel()
        currentBeamRendezvousId = generateRendezvousId()
        setBackgroundSharingEnabled(true)

        autoTimeoutJob = scope.launch {
            delay(durationMinutes * 60 * 1000L)
            deactivateBeam()
        }
    }

    /**
     * Deactivates TAP Beam, setting HCE to dormant state.
     */
    fun deactivateBeam() {
        autoTimeoutJob?.cancel()
        autoTimeoutJob = null
        setBackgroundSharingEnabled(false)
    }

    private fun generateRendezvousId(): String {
        return "beam_${UUID.randomUUID().toString().replace("-", "").take(8)}"
    }

    private fun notifyTileStateChanged() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, TapShareTileService::class.java)
                )
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val PREFS_NAME = "tap_nfc_settings_prefs"
        private const val KEY_BACKGROUND_SHARING = "key_background_nfc_sharing"

        @Volatile
        private var instance: NfcSettingsRepository? = null

        fun getInstance(context: Context): NfcSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: NfcSettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

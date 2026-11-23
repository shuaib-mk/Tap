package dev.tap.app.nfc

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages persistent configuration for TAP's NFC Host Card Emulation (HCE)
 * and direct phone-to-phone contact sharing.
 */
class NfcSettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isBackgroundSharingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BACKGROUND_SHARING, true)
    )
    val isBackgroundSharingEnabled: StateFlow<Boolean> = _isBackgroundSharingEnabled.asStateFlow()

    fun isBackgroundSharingEnabled(): Boolean {
        return prefs.getBoolean(KEY_BACKGROUND_SHARING, true)
    }

    fun setBackgroundSharingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_SHARING, enabled).apply()
        _isBackgroundSharingEnabled.value = enabled
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

package dev.tap.app.nfc

import android.content.Context
import android.nfc.NfcAdapter

data class NfcCapabilities(
    val hardwarePresent: Boolean,
    val enabled: Boolean,
    val hceSupported: Boolean
) {
    companion object {
        fun from(context: Context): NfcCapabilities {
            val adapter = NfcAdapter.getDefaultAdapter(context)
            val pm = context.packageManager
            return NfcCapabilities(
                hardwarePresent = adapter != null,
                enabled = adapter?.isEnabled == true,
                hceSupported = pm.hasSystemFeature("android.hardware.nfc.hce")
            )
        }
    }
}

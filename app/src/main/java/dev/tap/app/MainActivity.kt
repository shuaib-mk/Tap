package dev.tap.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dev.tap.app.discovery.nfc.NfcSessionExchange
import dev.tap.app.navigation.TapScreen
import dev.tap.app.nfc.TapHostApduService
import dev.tap.app.permissions.PermissionHelper
import dev.tap.app.ui.TapApp
import dev.tap.app.ui.theme.TapTheme
import dev.tap.app.ui.viewmodels.TransferViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: TransferViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled reactively by UI and view models
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request initial required permissions
        requestAppPermissions()

        // Handle intent (Sharesheet or NFC)
        handleIntent(intent)

        // Dynamically manage Reader Mode vs HCE Card Emulation based on active screen
        lifecycleScope.launch {
            viewModel.currentScreen.collect { screen ->
                updateNfcOperatingMode(screen)
            }
        }

        setContent {
            TapTheme {
                TapApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNfcOperatingMode(viewModel.currentScreen.value)
    }

    override fun onPause() {
        super.onPause()
        NfcSessionExchange.disableReaderMode(this)
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter != null) {
            try {
                CardEmulation.getInstance(nfcAdapter).unsetPreferredService(this)
            } catch (_: Exception) {}
        }
    }

    private fun updateNfcOperatingMode(screen: TapScreen) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this) ?: return
        if (!nfcAdapter.isEnabled) return

        if (screen is TapScreen.NfcCardWriter) {
            // Screen requires active RF polling to write external physical NFC tags
            NfcSessionExchange.enableReaderMode(
                this,
                onSessionDiscovered = { offer ->
                    viewModel.connectAndSend(offer)
                },
                onTagDiscovered = { tag ->
                    viewModel.handleNfcTagDiscovered(tag)
                }
            )
            try {
                CardEmulation.getInstance(nfcAdapter).unsetPreferredService(this)
            } catch (_: Exception) {}
        } else if (screen is TapScreen.ProximityShare && viewModel.isProximityReceiverRunning.value) {
            // Receiver tab is active: enable Reader Mode to actively poll for sender's HCE tag
            NfcSessionExchange.enableReaderMode(
                this,
                onSessionDiscovered = { offer ->
                    viewModel.connectAndSend(offer)
                },
                onProximityDiscovered = { json ->
                    viewModel.handleIncomingProximityNdefJson(json)
                }
            )
            try {
                CardEmulation.getInstance(nfcAdapter).unsetPreferredService(this)
            } catch (_: Exception) {}
        } else {
            // General operation (Contact Card, Home, Settings, Physical Tap to Share Sender):
            // MUST disable Reader Mode so device acts as a passive HCE CARD for other phones!
            // Two reader/pollers cannot read each other; this device must emulate a Type 4 Tag.
            NfcSessionExchange.disableReaderMode(this)
            try {
                val cardEmulation = CardEmulation.getInstance(nfcAdapter)
                cardEmulation.setPreferredService(this, ComponentName(this, TapHostApduService::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return

        // 1. Android Sharesheet integration: receive files shared from other apps
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
                }
                if (uri != null) {
                    viewModel.addFiles(listOf(uri))
                    viewModel.navigateTo(TapScreen.FilePicker)
                } else {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                        viewModel.addLink(text)
                        viewModel.navigateTo(TapScreen.FilePicker)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    viewModel.addFiles(uris)
                    viewModel.navigateTo(TapScreen.FilePicker)
                }
            }
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                if (rawMessages != null && rawMessages.isNotEmpty()) {
                    val ndefMsg = rawMessages[0] as? android.nfc.NdefMessage
                    ndefMsg?.let { msg ->
                        val proximityJson = NfcSessionExchange.parseProximityNdefMessage(msg)
                        if (proximityJson != null) {
                            viewModel.navigateTo(TapScreen.ProximityShare)
                            viewModel.handleIncomingProximityNdefJson(proximityJson)
                        } else {
                            NfcSessionExchange.parseNdefMessage(msg)?.let { offer ->
                                viewModel.connectAndSend(offer)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf<String>()
        permissions.addAll(PermissionHelper.getBluetoothPermissions())
        permissions.addAll(PermissionHelper.getNotificationPermission())

        val missing = PermissionHelper.getMissingPermissions(this, permissions)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

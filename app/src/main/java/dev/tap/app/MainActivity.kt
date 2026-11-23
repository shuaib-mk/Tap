package dev.tap.app

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.tap.app.discovery.nfc.NfcSessionExchange
import dev.tap.app.navigation.TapScreen
import dev.tap.app.permissions.PermissionHelper
import dev.tap.app.ui.TapApp
import dev.tap.app.ui.theme.TapTheme
import dev.tap.app.ui.viewmodels.TransferViewModel

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

        setContent {
            TapTheme {
                TapApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable NFC reader mode for device-to-device tapping and tag writing
        NfcSessionExchange.enableReaderMode(
            this,
            onSessionDiscovered = { offer ->
                viewModel.connectAndSend(offer)
            },
            onTagDiscovered = { tag ->
                viewModel.handleNfcTagDiscovered(tag)
            }
        )
    }

    override fun onPause() {
        super.onPause()
        NfcSessionExchange.disableReaderMode(this)
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
                        NfcSessionExchange.parseNdefMessage(msg)?.let { offer ->
                            viewModel.connectAndSend(offer)
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

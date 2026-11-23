package dev.tap.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dev.tap.app.contacts.ContactCard
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

        // Handle intent (Sharesheet, deep link, or NFC)
        handleIntent(intent)

        // Dynamically manage Reader Mode vs HCE Card Emulation based on active screen and receive state
        lifecycleScope.launch {
            viewModel.currentScreen.collect { screen ->
                updateNfcOperatingMode(screen, viewModel.isReceiveModeActive.value)
            }
        }

        lifecycleScope.launch {
            viewModel.isReceiveModeActive.collect { isReceiveActive ->
                updateNfcOperatingMode(viewModel.currentScreen.value, isReceiveActive)
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
        updateNfcOperatingMode(viewModel.currentScreen.value, viewModel.isReceiveModeActive.value)
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

    private fun updateNfcOperatingMode(screen: TapScreen, isReceiveActive: Boolean) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this) ?: return
        if (!nfcAdapter.isEnabled) return

        val shouldEnableReaderMode = screen is TapScreen.NfcCardWriter ||
                screen is TapScreen.QrScanner ||
                screen is TapScreen.Receive ||
                isReceiveActive ||
                (screen is TapScreen.ProximityShare && viewModel.isProximityReceiverRunning.value)

        if (shouldEnableReaderMode) {
            // Enable Reader Mode to actively poll for nearby tags/cards
            NfcSessionExchange.enableReaderMode(
                activity = this,
                onSessionDiscovered = { offer ->
                    viewModel.connectAndSend(offer)
                },
                onTagDiscovered = { tag ->
                    viewModel.handleNfcTagDiscovered(tag)
                },
                onProximityDiscovered = { json ->
                    viewModel.handleIncomingProximityNdefJson(json)
                },
                onSessionCodeDiscovered = { code ->
                    Toast.makeText(this, "Session Code Detected: $code", Toast.LENGTH_SHORT).show()
                    viewModel.joinAnywhereShare(code)
                    viewModel.navigateTo(TapScreen.TransferProgress)
                },
                onContactCardDiscovered = { card ->
                    showContactCardImportPrompt(card)
                }
            )
            try {
                CardEmulation.getInstance(nfcAdapter).unsetPreferredService(this)
            } catch (_: Exception) {}
        } else {
            // General operation: Emulate Type 4 Tag HCE so other phones can tap this device
            NfcSessionExchange.disableReaderMode(this)
            try {
                val cardEmulation = CardEmulation.getInstance(nfcAdapter)
                cardEmulation.setPreferredService(this, ComponentName(this, TapHostApduService::class.java))
            } catch (_: Exception) {}
        }
    }

    private fun showContactCardImportPrompt(card: ContactCard) {
        Toast.makeText(this, "Received contact for ${card.displayName}", Toast.LENGTH_SHORT).show()
        val insertIntent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
            putExtra(ContactsContract.Intents.Insert.NAME, card.displayName)
            putExtra(ContactsContract.Intents.Insert.COMPANY, card.company)
            putExtra(ContactsContract.Intents.Insert.JOB_TITLE, card.jobTitle)
            putExtra(ContactsContract.Intents.Insert.PHONE, card.phone)
            putExtra(ContactsContract.Intents.Insert.EMAIL, card.email)
            putExtra(ContactsContract.Intents.Insert.NOTES, card.notes)
        }
        try {
            startActivity(insertIntent)
        } catch (_: Exception) {}
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
                    viewModel.stageAnywhereUris(listOf(uri))
                    viewModel.navigateTo(TapScreen.FilePicker)
                } else {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                        if (text.contains("tap.dev/s/")) {
                            val code = text.substringAfter("tap.dev/s/").substringBefore("?").substringBefore("/").trim()
                            if (code.isNotBlank()) {
                                viewModel.joinAnywhereShare(code)
                                viewModel.navigateTo(TapScreen.TransferProgress)
                            }
                        } else {
                            viewModel.addLink(text)
                            viewModel.navigateTo(TapScreen.FilePicker)
                        }
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
                    viewModel.stageAnywhereUris(uris)
                    viewModel.navigateTo(TapScreen.FilePicker)
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.dataString
                if (!data.isNullOrBlank() && data.contains("tap.dev/s/")) {
                    val code = data.substringAfter("tap.dev/s/").substringBefore("?").substringBefore("/").trim()
                    if (code.isNotBlank()) {
                        viewModel.joinAnywhereShare(code)
                        viewModel.navigateTo(TapScreen.TransferProgress)
                    }
                }
            }
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                if (rawMessages != null && rawMessages.isNotEmpty()) {
                    val ndefMsg = rawMessages[0] as? android.nfc.NdefMessage
                    ndefMsg?.let { msg ->
                        // 1. Proximity JSON
                        val proximityJson = NfcSessionExchange.parseProximityNdefMessage(msg)
                        if (proximityJson != null) {
                            viewModel.navigateTo(TapScreen.ProximityShare)
                            viewModel.handleIncomingProximityNdefJson(proximityJson)
                            return
                        }

                        // 2. Anywhere Session Code
                        val sessionCode = NfcSessionExchange.parseSessionCode(msg)
                        if (sessionCode != null) {
                            Toast.makeText(this, "Session Code Detected: $sessionCode", Toast.LENGTH_SHORT).show()
                            viewModel.joinAnywhereShare(sessionCode)
                            viewModel.navigateTo(TapScreen.TransferProgress)
                            return
                        }

                        // 3. Contact Card vCard
                        val card = NfcSessionExchange.parseVCard(msg)
                        if (card != null) {
                            showContactCardImportPrompt(card)
                            return
                        }

                        // 4. Legacy SessionOffer
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

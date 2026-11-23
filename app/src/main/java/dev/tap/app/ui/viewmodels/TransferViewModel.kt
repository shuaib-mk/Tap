package dev.tap.app.ui.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tap.app.discovery.DiscoveryManager
import dev.tap.app.discovery.DiscoveryState
import dev.tap.app.discovery.ble.BleDiscovery
import dev.tap.app.discovery.qr.QrCodeGenerator
import dev.tap.app.history.TransferHistoryRepository
import dev.tap.app.history.data.TransferRecord
import dev.tap.app.navigation.TapScreen
import dev.tap.app.nfc.NfcCapabilities
import dev.tap.app.protocol.*
import dev.tap.app.security.SessionManager
import dev.tap.app.security.TapSession
import dev.tap.app.transfer.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager()
    val historyRepo = TransferHistoryRepository.getInstance(application)
    private val bleDiscovery = BleDiscovery(application)
    val discoveryManager = DiscoveryManager(application, bleDiscovery)

    private val transferServer = TransferServer(application, sessionManager, historyRepo)
    private val transferClient = TransferClient(application, sessionManager, historyRepo)

    // Navigation state
    private val _currentScreen = MutableStateFlow<TapScreen>(TapScreen.Home)
    val currentScreen: StateFlow<TapScreen> = _currentScreen.asStateFlow()

    private val _backStack = mutableListOf<TapScreen>(TapScreen.Home)

    // Home / Mode state
    private val _mode = MutableStateFlow("Send") // "Send" or "Receive"
    val mode: StateFlow<String> = _mode.asStateFlow()

    // Device identity
    val deviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    // Selected files
    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    val totalSelectedSize: Long get() = _selectedFiles.value.sumOf { it.sizeBytes }

    // Active session offer & QR code
    private val _activeSession = MutableStateFlow<TapSession?>(null)
    val activeSession: StateFlow<TapSession?> = _activeSession.asStateFlow()

    private val _sessionOffer = MutableStateFlow<SessionOffer?>(null)
    val sessionOffer: StateFlow<SessionOffer?> = _sessionOffer.asStateFlow()

    private val _qrBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val qrBitmap: StateFlow<android.graphics.Bitmap?> = _qrBitmap.asStateFlow()

    // Transfer Progress
    val serverProgress: StateFlow<TransferProgress> = transferServer.progress
    val clientProgress: StateFlow<TransferProgress> = transferClient.progress

    val activeProgress: TransferProgress
        get() = if (_mode.value == "Send") clientProgress.value else serverProgress.value

    // Pending incoming request on receiver
    val pendingIncomingRequest: StateFlow<TransferRequest?> = transferServer.pendingRequest

    // Capabilities
    val nfcCapabilities: NfcCapabilities get() = NfcCapabilities.from(getApplication())
    val isBleAvailable: Boolean get() = bleDiscovery.isAvailable && bleDiscovery.isEnabled

    init {
        // Collect server progress to trigger foreground service notifications
        viewModelScope.launch {
            serverProgress.collect { prog ->
                when (prog.state) {
                    TransferState.TRANSFERRING -> {
                        TapTransferService.start(
                            getApplication(),
                            "Receiving files...",
                            "${prog.currentFileName} (${prog.formattedPercent()})"
                        )
                    }
                    TransferState.COMPLETE -> {
                        TapTransferService.stop(getApplication())
                        navigateTo(TapScreen.TransferComplete)
                    }
                    TransferState.FAILED, TransferState.CANCELLED -> {
                        TapTransferService.stop(getApplication())
                    }
                    else -> {}
                }
            }
        }

        // Collect client progress
        viewModelScope.launch {
            clientProgress.collect { prog ->
                when (prog.state) {
                    TransferState.TRANSFERRING -> {
                        TapTransferService.start(
                            getApplication(),
                            "Sending files...",
                            "${prog.currentFileName} (${prog.formattedPercent()})"
                        )
                    }
                    TransferState.COMPLETE -> {
                        TapTransferService.stop(getApplication())
                        navigateTo(TapScreen.TransferComplete)
                    }
                    TransferState.FAILED, TransferState.CANCELLED -> {
                        TapTransferService.stop(getApplication())
                    }
                    else -> {}
                }
            }
        }

        // Collect incoming requests to navigate receiver to approval screen
        viewModelScope.launch {
            pendingIncomingRequest.collect { req ->
                if (req != null) {
                    navigateTo(TapScreen.ReceiverApproval)
                }
            }
        }
    }

    // Navigation
    fun navigateTo(screen: TapScreen) {
        _backStack.add(screen)
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        if (_backStack.size > 1) {
            _backStack.removeAt(_backStack.size - 1)
            _currentScreen.value = _backStack.last()
            return true
        }
        return false
    }

    // Mode
    fun setMode(newMode: String) {
        _mode.value = newMode
        if (newMode == "Receive") {
            prepareReceiverSession()
        } else {
            transferServer.stop()
        }
    }

    fun prepareReceiverSession() {
        viewModelScope.launch {
            val session = sessionManager.createSession(deviceName, ConnectionMethod.QR)
            _activeSession.value = session
            val offer = session.toSessionOffer(
                hostAddress = TransferServer.getLocalIpAddress(),
                port = TapProtocol.DEFAULT_PORT
            )
            _sessionOffer.value = offer
            _qrBitmap.value = QrCodeGenerator.generateSessionQr(offer)

            // Start TCP server
            transferServer.start(viewModelScope)
            // Broadcast via BLE
            discoveryManager.startAdvertising(offer)
        }
    }

    fun addFiles(uris: List<Uri>) {
        val current = _selectedFiles.value.toMutableList()
        uris.forEach { uri ->
            SelectedFile.fromUri(getApplication(), uri)?.let { file ->
                if (current.none { it.uri == file.uri }) {
                    current.add(file)
                }
            }
        }
        _selectedFiles.value = current
    }

    fun addLink(url: String) {
        val file = SelectedFile.fromLink(getApplication(), url)
        _selectedFiles.value = _selectedFiles.value + file
    }

    fun addContact(name: String, vcardContent: String) {
        val file = SelectedFile.fromContact(getApplication(), name, vcardContent)
        _selectedFiles.value = _selectedFiles.value + file
    }

    fun removeFile(file: SelectedFile) {
        _selectedFiles.value = _selectedFiles.value.filter { it.uri != file.uri }
    }

    fun clearFiles() {
        _selectedFiles.value = emptyList()
    }

    // Sender initiation
    fun connectAndSend(offer: SessionOffer) {
        viewModelScope.launch {
            val session = sessionManager.createSession(deviceName, offer.connectionMethod)
            _activeSession.value = session
            navigateTo(TapScreen.TransferProgress)

            val host = if (offer.hostAddress.isNotBlank()) offer.hostAddress else "127.0.0.1"
            transferClient.sendFiles(
                host = host,
                port = offer.port,
                sessionId = session.sessionId,
                senderName = deviceName,
                files = _selectedFiles.value
            )
        }
    }

    // Receiver decision
    fun acceptIncomingTransfer() {
        transferServer.respondToRequest(true)
        navigateTo(TapScreen.TransferProgress)
    }

    fun rejectIncomingTransfer() {
        transferServer.respondToRequest(false)
        navigateBack()
    }

    fun cancelActiveTransfer() {
        if (_mode.value == "Send") {
            transferClient.cancel()
        } else {
            transferServer.stop()
        }
        TapTransferService.stop(getApplication())
        navigateTo(TapScreen.Home)
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepo.clearAll()
        }
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch {
            historyRepo.deleteById(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        transferServer.stop()
        transferClient.cancel()
    }
}

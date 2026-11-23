package dev.tap.app.ui.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.nfc.Tag
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tap.app.capabilities.CompatibilityEngine
import dev.tap.app.capabilities.DeviceCapabilities
import dev.tap.app.contacts.ContactCard
import dev.tap.app.contacts.ContactCardRepository
import dev.tap.app.discovery.DiscoveryManager
import dev.tap.app.discovery.DiscoveryState
import dev.tap.app.discovery.ble.BleDiscovery
import dev.tap.app.discovery.nfc.NfcTagWriter
import dev.tap.app.discovery.qr.QrCodeGenerator
import dev.tap.app.history.TransferHistoryRepository
import dev.tap.app.history.data.TransferRecord
import dev.tap.app.native.NativeSharingAdapter
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

data class BeamedContactEvent(
    val card: ContactCard,
    val isTwoWay: Boolean
)

sealed class NfcWriteUiState {
    object Idle : NfcWriteUiState()
    object WaitingForTag : NfcWriteUiState()
    object Writing : NfcWriteUiState()
    data class Success(val bytesWritten: Int, val tagCapacity: Int) : NfcWriteUiState()
    data class Error(val message: String) : NfcWriteUiState()
}

enum class NfcMode {
    MODE_A_TAP_TO_TAP,
    MODE_B_CONTACT_CARD,
    MODE_C_BACKGROUND_HCE
}

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager()
    val historyRepo = TransferHistoryRepository.getInstance(application)
    private val bleDiscovery = BleDiscovery(application)
    val discoveryManager = DiscoveryManager(application, bleDiscovery)

    private val transferServer = TransferServer(application, sessionManager, historyRepo)
    private val transferClient = TransferClient(application, sessionManager, historyRepo)
    private val localHttpServer = LocalHttpServer(application)
    private val contactRepo = ContactCardRepository.getInstance(application)
    val proximityEngine = dev.tap.app.proximity.ProximitySharingEngine(application)
    val stagedProximityFiles = proximityEngine.stagedFiles
    val proximityEngineState = proximityEngine.engineState
    val proximityProgress = proximityEngine.progress
    val isProximityReceiverRunning = proximityEngine.isReceiverRunning
    val pendingProximityApproval = proximityEngine.pendingApproval
    val discoveredP2pPeers = proximityEngine.discoveredPeers
    val isPhysicalTapToShareActive = proximityEngine.isPhysicalTapToShareActive
    val currentSasCode = proximityEngine.currentSasCode
    val proximityWebFallbackUrl = proximityEngine.webFallbackUrl

    // WebRTC Anywhere Engine (v1.4.0)
    val anywhereEngine = dev.tap.app.webrtc.AnywhereTransferEngine(application, viewModelScope)
    val anywhereState = anywhereEngine.engineState
    val anywhereProgress = anywhereEngine.progress
    val stagedAnywhereFiles = anywhereEngine.stagedFiles
    val isInternetAvailable: Boolean get() = anywhereEngine.isInternetAvailable

    fun stageAnywhereUris(uris: List<Uri>) {
        anywhereEngine.stageUris(uris)
        proximityEngine.stageUris(uris)
    }

    fun removeStagedAnywhereFile(file: dev.tap.app.proximity.ProximityFile) {
        anywhereEngine.removeStagedFile(file)
        proximityEngine.removeStagedFile(file)
    }

    fun clearStagedAnywhereFiles() {
        anywhereEngine.clearStagedFiles()
        proximityEngine.clearStagedFiles()
    }

    fun startAnywhereShare() {
        anywhereEngine.startAnywhereShare()
    }

    fun joinAnywhereShare(codeOrId: String) {
        anywhereEngine.joinAnywhereShare(codeOrId)
    }

    fun respondToTransferConsent(accepted: Boolean) {
        anywhereEngine.respondToTransferConsent(accepted)
    }

    fun pauseTransfer() {
        anywhereEngine.pauseTransfer()
    }

    fun resumeTransfer() {
        anywhereEngine.resumeTransfer()
    }

    fun cancelAnywhereTransfer() {
        anywhereEngine.cancelActiveTransfer()
    }

    fun startOfflineNearbyShare() {
        anywhereEngine.startOfflineNearbyShare()
    }

    fun stageProximityUris(uris: List<Uri>) {
        proximityEngine.stageUris(uris)
    }

    fun removeProximityFile(file: dev.tap.app.proximity.ProximityFile) {
        proximityEngine.removeStagedFile(file)
    }

    fun clearProximityFiles() {
        proximityEngine.clearStagedFiles()
    }

    fun activatePhysicalTapToShare() {
        proximityEngine.activatePhysicalTapToShare(viewModelScope)
    }

    fun deactivatePhysicalTapToShare() {
        proximityEngine.deactivatePhysicalTapToShare()
    }

    fun handleIncomingProximityNdefJson(json: String) {
        proximityEngine.handleIncomingProximityNdefJson(json, viewModelScope)
    }

    fun launchQuickShareHandoff() {
        proximityEngine.launchSamsungQuickShareHandoff()
    }

    fun cancelProximityTransfer() {
        proximityEngine.cancelActiveTransfer()
    }

    fun startProximityReceiver() {
        proximityEngine.startReceiverMode(viewModelScope)
    }

    fun stopProximityReceiver() {
        proximityEngine.stopReceiverMode()
    }

    fun respondToProximityApproval(accepted: Boolean) {
        proximityEngine.respondToApproval(accepted)
    }

    fun startProximityDiscovery() {
        proximityEngine.startProximityDiscovery(viewModelScope)
    }

    fun stopProximityDiscovery() {
        proximityEngine.stopProximityDiscovery()
    }

    fun sendProximityToHost(host: String) {
        proximityEngine.sendToHost(host, scope = viewModelScope)
    }

    // Navigation state
    private val _currentScreen = MutableStateFlow<TapScreen>(TapScreen.Home)
    val currentScreen: StateFlow<TapScreen> = _currentScreen.asStateFlow()

    private val _backStack = mutableListOf<TapScreen>(TapScreen.Home)

    // Mode state ("Send" vs "Receive")
    private val _mode = MutableStateFlow("Send")
    val mode: StateFlow<String> = _mode.asStateFlow()

    // Device identity & Capabilities
    val deviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    private val _capabilities = MutableStateFlow(CompatibilityEngine.detectCapabilities(application))
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    // Contact Card
    private val _contactCard = MutableStateFlow(contactRepo.getContactCard())
    val contactCard: StateFlow<ContactCard> = _contactCard.asStateFlow()

    // NFC Mode & Background Tap Sharing
    private val nfcSettingsRepo = dev.tap.app.nfc.NfcSettingsRepository.getInstance(application)
    val isBackgroundTapSharing: StateFlow<Boolean> = nfcSettingsRepo.isBackgroundSharingEnabled

    fun setBackgroundTapSharing(enabled: Boolean) {
        nfcSettingsRepo.setBackgroundSharingEnabled(enabled)
    }

    fun activateBeam(durationMinutes: Int = 5) {
        nfcSettingsRepo.activateBeam(durationMinutes)
    }

    fun deactivateBeam() {
        nfcSettingsRepo.deactivateBeam()
    }

    // In-App Apple NameDrop-style Beamed Contact Event
    private val _beamedContactEvent = MutableStateFlow<BeamedContactEvent?>(null)
    val beamedContactEvent: StateFlow<BeamedContactEvent?> = _beamedContactEvent.asStateFlow()

    fun showBeamedContact(card: ContactCard, isTwoWay: Boolean) {
        _beamedContactEvent.value = BeamedContactEvent(card, isTwoWay)
    }

    fun dismissBeamedContact() {
        _beamedContactEvent.value = null
    }

    private val _nfcMode = MutableStateFlow(NfcMode.MODE_B_CONTACT_CARD)
    val nfcMode: StateFlow<NfcMode> = _nfcMode.asStateFlow()

    // NFC Tag Writer State
    private val _nfcWriteState = MutableStateFlow<NfcWriteUiState>(NfcWriteUiState.Idle)
    val nfcWriteState: StateFlow<NfcWriteUiState> = _nfcWriteState.asStateFlow()

    // Selected files for sending
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

    // Local Web Server State (No-app receiver mode)
    private val _isHttpServerRunning = MutableStateFlow(false)
    val isHttpServerRunning: StateFlow<Boolean> = _isHttpServerRunning.asStateFlow()

    private val _httpServerUrl = MutableStateFlow("")
    val httpServerUrl: StateFlow<String> = _httpServerUrl.asStateFlow()

    // Transfer Progress
    val serverProgress: StateFlow<TransferProgress> = transferServer.progress
    val clientProgress: StateFlow<TransferProgress> = transferClient.progress

    val activeProgress: TransferProgress
        get() {
            val anywhere = anywhereProgress.value
            if (anywhere.totalBytes > 0 || anywhere.isComplete) {
                return TransferProgress(
                    state = if (anywhere.isComplete) TransferState.COMPLETE else TransferState.TRANSFERRING,
                    bytesTransferred = anywhere.bytesTransferred,
                    totalBytes = anywhere.totalBytes,
                    currentFileName = anywhere.currentFileName,
                    currentFileIndex = anywhere.currentFileIndex,
                    totalFiles = anywhere.totalFiles,
                    speedBytesPerSec = anywhere.speedBytesPerSec
                )
            }
            return if (_mode.value == "Send") clientProgress.value else serverProgress.value
        }

    private val _isReceiveModeActive = MutableStateFlow(false)
    val isReceiveModeActive: StateFlow<Boolean> = _isReceiveModeActive.asStateFlow()

    fun setReceiveModeActive(active: Boolean) {
        _isReceiveModeActive.value = active
    }

    // Pending incoming request on receiver
    val pendingIncomingRequest: StateFlow<TransferRequest?> = transferServer.pendingRequest

    val nfcCapabilities: NfcCapabilities get() = NfcCapabilities.from(getApplication())
    val isBleAvailable: Boolean get() = bleDiscovery.isAvailable && bleDiscovery.isEnabled

    init {
        // Collect server progress
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

        // Collect anywhereEngine state for automatic screen navigation
        viewModelScope.launch {
            anywhereEngine.engineState.collect { state ->
                when (state) {
                    is dev.tap.app.webrtc.AnywhereEngineState.IncomingTransferRequest -> {
                        navigateTo(TapScreen.ReceiverApproval)
                    }
                    is dev.tap.app.webrtc.AnywhereEngineState.Transferring -> {
                        if (_currentScreen.value != TapScreen.TransferProgress) {
                            navigateTo(TapScreen.TransferProgress)
                        }
                    }
                    is dev.tap.app.webrtc.AnywhereEngineState.Completed -> {
                        navigateTo(TapScreen.TransferComplete)
                    }
                    else -> {}
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

    fun refreshCapabilities() {
        _capabilities.value = CompatibilityEngine.detectCapabilities(getApplication())
    }

    // Contact Card Management
    fun updateContactCard(card: ContactCard) {
        _contactCard.value = card
        contactRepo.saveContactCard(card)
    }

    fun toggleContactCardField(field: String) {
        val updated = _contactCard.value.toggleField(field)
        updateContactCard(updated)
    }

    fun resetContactCard() {
        val def = contactRepo.resetToDefault()
        _contactCard.value = def
    }

    fun setNfcMode(mode: NfcMode) {
        _nfcMode.value = mode
    }

    // NFC Tag Writer
    fun startNfcTagWriter() {
        _nfcWriteState.value = NfcWriteUiState.WaitingForTag
        navigateTo(TapScreen.NfcCardWriter)
    }

    fun resetNfcWriteState() {
        _nfcWriteState.value = NfcWriteUiState.Idle
    }

    fun handleNfcTagDiscovered(tag: Tag) {
        if (_nfcWriteState.value is NfcWriteUiState.WaitingForTag || _nfcWriteState.value is NfcWriteUiState.Writing) {
            _nfcWriteState.value = NfcWriteUiState.Writing
            viewModelScope.launch {
                val fallbackUrl = if (_isHttpServerRunning.value) _httpServerUrl.value else _contactCard.value.website
                val res = NfcTagWriter.writeContactCard(tag, _contactCard.value, fallbackUrl)
                when (res) {
                    is NfcTagWriter.WriteResult.Success -> {
                        _nfcWriteState.value = NfcWriteUiState.Success(res.bytesWritten, res.tagCapacity)
                    }
                    is NfcTagWriter.WriteResult.Error -> {
                        _nfcWriteState.value = NfcWriteUiState.Error(res.message)
                    }
                }
            }
        }
    }

    // Local HTTP Server (Universal browser sharing for non-TAP devices)
    fun toggleLocalHttpServer(includeFiles: Boolean = true) {
        if (_isHttpServerRunning.value) {
            stopLocalHttpServer()
        } else {
            startLocalHttpServer(includeFiles)
        }
    }

    fun startLocalHttpServer(includeFiles: Boolean = true) {
        val ip = _capabilities.value.localIpAddress ?: TransferServer.getLocalIpAddress()
        val card = _contactCard.value
        val files = if (includeFiles) _selectedFiles.value else emptyList()

        val res = localHttpServer.start(hostAddress = ip, port = LocalHttpServer.DEFAULT_WEB_PORT, contactCard = card, files = files)
        if (res.isSuccess) {
            _isHttpServerRunning.value = true
            _httpServerUrl.value = res.getOrThrow()
            _qrBitmap.value = QrCodeGenerator.generateQrBitmap(_httpServerUrl.value)
        }
    }

    fun stopLocalHttpServer() {
        localHttpServer.stop()
        _isHttpServerRunning.value = false
        _httpServerUrl.value = ""
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

    // Native Sharesheet Integration
    fun shareContactViaSharesheet() {
        NativeSharingAdapter.shareContactCard(getApplication(), _contactCard.value)
    }

    fun shareSelectedFilesViaSharesheet() {
        val uris = ArrayList(_selectedFiles.value.map { it.uri })
        if (uris.isNotEmpty()) {
            NativeSharingAdapter.shareFiles(getApplication(), uris)
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
        localHttpServer.stop()
        proximityEngine.stopReceiverMode()
    }
}

package dev.tap.app.navigation

sealed class TapScreen {
    object Home : TapScreen()
    object Send : TapScreen()
    object Receive : TapScreen()
    object NearbyDevices : TapScreen()
    object NfcReadiness : TapScreen()
    object ContactCard : TapScreen()
    object NfcCardWriter : TapScreen()
    object QrScanner : TapScreen()
    object ReceiverApproval : TapScreen()
    object TransferProgress : TapScreen()
    object TransferComplete : TapScreen()
    object History : TapScreen()
    object Settings : TapScreen()
    object CompatibilityDiagnostics : TapScreen()
    object ProximityShare : TapScreen()
    object About : TapScreen()
    object AnywhereTransfer : TapScreen()
    object Activity : TapScreen()

    // Backward compatibility aliases
    object FilePicker : TapScreen()
    object Discovery : TapScreen()
    object QrConnect : TapScreen()
}

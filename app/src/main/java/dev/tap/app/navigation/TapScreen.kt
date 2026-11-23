package dev.tap.app.navigation

sealed class TapScreen {
    object Home : TapScreen()
    object FilePicker : TapScreen()
    object Discovery : TapScreen()
    object QrConnect : TapScreen()
    object ReceiverApproval : TapScreen()
    object TransferProgress : TapScreen()
    object TransferComplete : TapScreen()
    object History : TapScreen()
    object Settings : TapScreen()
    object About : TapScreen()
}

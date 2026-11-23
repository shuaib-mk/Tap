package dev.tap.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tap.app.navigation.TapScreen
import dev.tap.app.ui.components.TapInk
import dev.tap.app.ui.screens.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun TapApp(viewModel: TransferViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    BackHandler(enabled = currentScreen !is TapScreen.Home) {
        viewModel.navigateBack()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "screenTransition"
        ) { screen ->
            when (screen) {
                is TapScreen.Home -> HomeScreen(viewModel)
                is TapScreen.Send, is TapScreen.FilePicker -> FilePickerScreen(viewModel)
                is TapScreen.Receive, is TapScreen.QrConnect -> QrConnectScreen(viewModel)
                is TapScreen.NearbyDevices, is TapScreen.Discovery -> DiscoveryScreen(viewModel)
                is TapScreen.NfcReadiness -> NfcReadinessScreen(viewModel)
                is TapScreen.ContactCard -> ContactCardScreen(viewModel)
                is TapScreen.NfcCardWriter -> NfcCardWriterScreen(viewModel)
                is TapScreen.QrScanner -> QrScannerScreen(viewModel)
                is TapScreen.ReceiverApproval -> ReceiverApprovalScreen(viewModel)
                is TapScreen.TransferProgress, is TapScreen.AnywhereTransfer -> TransferProgressScreen(viewModel)
                is TapScreen.TransferComplete -> TransferCompleteScreen(viewModel)
                is TapScreen.History, is TapScreen.Activity -> HistoryScreen(viewModel)
                is TapScreen.Settings -> SettingsScreen(viewModel)
                is TapScreen.CompatibilityDiagnostics -> CompatibilityDiagnosticsScreen(viewModel)
                is TapScreen.About -> AboutScreen(viewModel)
                is TapScreen.ProximityShare -> ProximityShareScreen(viewModel)
            }
        }
    }
}

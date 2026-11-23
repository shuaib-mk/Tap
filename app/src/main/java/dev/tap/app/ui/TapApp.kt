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
                is TapScreen.FilePicker -> FilePickerScreen(viewModel)
                is TapScreen.Discovery -> DiscoveryScreen(viewModel)
                is TapScreen.QrConnect -> QrConnectScreen(viewModel)
                is TapScreen.ReceiverApproval -> ReceiverApprovalScreen(viewModel)
                is TapScreen.TransferProgress -> TransferProgressScreen(viewModel)
                is TapScreen.TransferComplete -> TransferCompleteScreen(viewModel)
                is TapScreen.History -> HistoryScreen(viewModel)
                is TapScreen.Settings -> SettingsScreen(viewModel)
                is TapScreen.About -> AboutScreen(viewModel)
            }
        }
    }
}

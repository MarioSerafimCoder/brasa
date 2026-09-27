package com.brasa.tv.app.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.app.BrasaViewModel
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.feature.network.NetworkDiagnosticsScreen
import com.brasa.tv.feature.network.NetworkDiagnosticsViewModel
import com.brasa.tv.feature.profiles.*
import com.brasa.tv.feature.settings.SettingsScreen
import com.brasa.tv.feature.update.*

internal fun NavGraphBuilder.settingsRoutes(
    nav: NavHostController,
    vm: BrasaViewModel,
    stateRef: State<BrasaUiState>,
    container: AppContainer,
    updateVm: UpdateViewModel,
    updateStateRef: State<UpdateUiState>,
    startupUpdateRequiredRef: State<Boolean>,
    finishStartupUpdate: () -> Unit,
    requestInstallPermission: () -> Unit,
) {
    composable(Routes.Settings) {
        val state by stateRef
        val progressState by container.progressSync.state.collectAsState()
        SettingsScreen(
            state,
            container.settings,
            progressStatus = progressState.message,
            lastUpdateCheckAt = updateVm.lastCheckAt(),
            onUpdates = {
                updateVm.check(true)
                nav.navigate(Routes.Update)
            },
            onNetworkDiagnostics = { nav.navigate(Routes.NetworkDiagnostics) },
            onPlaybackHistory = { nav.navigate(Routes.PlaybackHistory) },
            onScanLibrary = vm::scanLibrary,
            onProfiles = { nav.navigate(Routes.Profiles) },
            onClearCache = vm::clearPlaybackCache,
            onLoadCache = vm::refreshCacheUsage,
            onResetPersonalization = vm::resetPersonalization,
            onAutoplayNext = vm::saveAutoplayNext,
            onForget = { vm.forget { nav.navigate(Routes.Server) { popUpTo(0) } } },
            onBack = { nav.popBackStack() },
        )
    }
    composable(Routes.NetworkDiagnostics) {
        val state by stateRef
        val networkVm: NetworkDiagnosticsViewModel =
            viewModel(
                factory =
                    NetworkDiagnosticsViewModel.Factory(
                        container.repository,
                        container.networkAccess,
                    )
            )
        val networkState by networkVm.state.collectAsState()
        NetworkDiagnosticsScreen(
            networkState,
            networkVm::load,
            networkVm::selectProfile,
            networkVm::runTest,
            networkVm::cancel,
        ) {
            nav.popBackStack()
        }
    }
    composable(Routes.Update) {
        val state by stateRef
        val updateState by updateStateRef
        val startupUpdateRequired by startupUpdateRequiredRef
        val visibleUpdateState =
            if (state.previewMode)
                UpdateUiState.Available(
                    UpdateManifest(
                        packageName = "com.brasa.tv",
                        versionCode = 42,
                        versionName = "2.4.0",
                        sizeBytes = 28_400_000,
                        releaseNotes =
                            listOf(
                                "Navegação mais fluida na TV",
                                "Player com carregamento aprimorado",
                                "Novo modo de atualização local",
                            ),
                    )
                )
            else updateState
        UpdateScreen(
            visibleUpdateState,
            onCheck = { updateVm.check(true) },
            onDownload = updateVm::download,
            onCancel = updateVm::cancel,
            onInstall = updateVm::prepareInstall,
            onPermission = { requestInstallPermission() },
            onBack = {
                if (startupUpdateRequired) {
                    if (!visibleUpdateState.requiresInstallation()) {
                        finishStartupUpdate()
                        updateVm.defer()
                        nav.navigate(Routes.Profiles) {
                            popUpTo(Routes.Server) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                } else {
                    updateVm.defer()
                    nav.popBackStack()
                }
            },
            required = visibleUpdateState.requiresInstallation(),
        )
    }
    composable(Routes.PlaybackHistory) {
        val state by stateRef
        com.brasa.tv.feature.settings.PlaybackHistoryScreen(container, state.profile?.id) {
            nav.popBackStack()
        }
    }
}

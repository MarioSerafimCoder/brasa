package com.brasa.tv.app.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.brasa.tv.BuildConfig
import com.brasa.tv.app.BrasaViewModel
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.feature.profiles.*
import com.brasa.tv.feature.update.*
import kotlinx.coroutines.delay

@Composable
fun BrasaNavHost(
    container: AppContainer,
    previewMode: Boolean = false,
    previewPage: String = "home",
    onExit: () -> Unit = {},
) {
    val nav = rememberNavController()
    val keyboard = LocalSoftwareKeyboardController.current
    val currentEntry by nav.currentBackStackEntryAsState()
    val vm: BrasaViewModel =
        viewModel(factory = BrasaViewModel.Factory(container.repository, container.playback))
    val updateVm: UpdateViewModel =
        viewModel(
            factory =
                UpdateViewModel.Factory(
                    container.updateRepository,
                    container.settings,
                    container.updatePreferences,
                    container.http,
                    container.context,
                    container.apkValidator,
                    container.packageInstaller,
                )
        )
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateVm.permissionReturned()
        }
    val state by vm.state.collectAsState()
    val updateState by updateVm.state.collectAsState()
    var startupUpdateRequired by remember { mutableStateOf(false) }
    val latestState = rememberUpdatedState(state)
    val latestUpdateState = rememberUpdatedState(updateState)
    val latestStartupUpdateRequired = rememberUpdatedState(startupUpdateRequired)
    val beginStartupUpdate = {
        startupUpdateRequired = true
        updateVm.check(true)
    }
    val previewRoute =
        previewPage.takeIf {
            it in
                setOf(
                    Routes.Server,
                    Routes.Pairing,
                    Routes.Profiles,
                    Routes.ProfilePin,
                    Routes.Home,
                    Routes.Movies,
                    Routes.Series,
                    Routes.MyList,
                    Routes.Collections,
                    Routes.Search,
                    Routes.Details,
                    Routes.Player,
                    Routes.Settings,
                    Routes.Update,
                )
        } ?: Routes.Home
    LaunchedEffect(previewMode, previewRoute) {
        if (previewMode && BuildConfig.DEBUG) vm.enablePreview(previewRoute)
    }
    LaunchedEffect(currentEntry?.destination?.route) {
        keyboard?.hide()
        delay(180)
        keyboard?.hide()
    }
    LaunchedEffect(startupUpdateRequired, updateState, currentEntry?.destination?.route) {
        if (!startupUpdateRequired || state.previewMode) return@LaunchedEffect
        val route = currentEntry?.destination?.route
        when (updateState) {
            UpdateUiState.UpToDate,
            is UpdateUiState.Installed -> {
                startupUpdateRequired = false
                if (route == Routes.Startup || route == Routes.Server || route == Routes.Update)
                    nav.navigate(Routes.Profiles) {
                        popUpTo(Routes.Startup) { inclusive = true }
                        launchSingleTop = true
                    }
            }
            is UpdateUiState.Available,
            is UpdateUiState.Error,
            is UpdateUiState.Downloading,
            is UpdateUiState.Validating,
            is UpdateUiState.Ready,
            is UpdateUiState.PermissionRequired,
            is UpdateUiState.Installing -> {
                if (route == Routes.Startup || route == Routes.Server)
                    nav.navigate(Routes.Update) { launchSingleTop = true }
            }
            else -> {}
        }
    }
    NavHost(
        navController = nav,
        startDestination = if (previewMode && BuildConfig.DEBUG) previewRoute else Routes.Startup,
    ) {
        sessionRoutes(nav, vm, latestState, container, beginStartupUpdate, onExit)
        catalogRoutes(nav, vm, latestState, container, updateVm, latestUpdateState)
        playbackRoutes(nav, vm, latestState, container)
        settingsRoutes(
            nav,
            vm,
            latestState,
            container,
            updateVm,
            latestUpdateState,
            latestStartupUpdateRequired,
            { startupUpdateRequired = false },
            { permissionLauncher.launch(updateVm.permissionIntent()) },
        )
    }
    val connectionIssue = state.connectionProblem
    if (
        connectionIssue != null &&
            !state.operations.session.loading &&
            currentEntry?.destination?.route !in
                setOf(Routes.Startup, Routes.Server, Routes.Pairing)
    ) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = vm::dismissConnectionProblem,
            properties =
                androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            com.brasa.tv.feature.server.ConnectionRecoveryScreen(
                connectionIssue,
                onRetry = { vm.restore {} },
                onServer = {
                    vm.dismissConnectionProblem()
                    nav.navigate(Routes.Server) { popUpTo(0) }
                },
                onPair = { nav.navigate(Routes.Server) { popUpTo(0) } },
            )
        }
    }
}

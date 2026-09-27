package com.brasa.tv.app.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.app.BrasaViewModel
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.feature.pairing.PairingScreen
import com.brasa.tv.feature.profiles.*
import com.brasa.tv.feature.server.ServerScreen
import com.brasa.tv.feature.startup.StartupScreen
import com.brasa.tv.feature.update.*

internal fun NavGraphBuilder.sessionRoutes(
    nav: NavHostController,
    vm: BrasaViewModel,
    stateRef: State<BrasaUiState>,
    container: AppContainer,
    beginStartupUpdate: () -> Unit,
    onExit: () -> Unit,
) {
    composable(Routes.Startup) {
        val state by stateRef
        val connectionProblem = state.connectionProblem
        LaunchedEffect(Unit) {
            vm.restore { paired ->
                if (paired) beginStartupUpdate()
                else nav.navigate(Routes.Server) { popUpTo(Routes.Startup) { inclusive = true } }
            }
        }
        if (connectionProblem != null && !state.operations.session.loading)
            com.brasa.tv.feature.server.ConnectionRecoveryScreen(
                connectionProblem,
                onRetry = {
                    vm.restore { paired ->
                        if (paired) beginStartupUpdate()
                        else
                            nav.navigate(Routes.Server) {
                                popUpTo(Routes.Startup) { inclusive = true }
                            }
                    }
                },
                onServer = { nav.navigate(Routes.Server) },
                onPair = { nav.navigate(Routes.Pairing) },
            )
        else
            StartupScreen(
                if (state.operations.session.loading) "Preparando sua experiência…"
                else "Conectando à sua biblioteca…"
            )
    }
    composable(Routes.Server) {
        val state by stateRef
        LaunchedEffect(Unit) {
            if (!state.previewMode) vm.restore { paired -> if (paired) beginStartupUpdate() }
        }
        ServerScreen(state, container, onPair = { nav.navigate(Routes.Pairing) }) { address ->
            vm.connect(address) { paired ->
                if (paired) beginStartupUpdate() else nav.navigate(Routes.Pairing)
            }
        }
    }
    composable(Routes.Pairing) {
        val state by stateRef
        PairingScreen(
            state,
            onStart = {
                vm.startPairing(it) {
                    beginStartupUpdate()
                    nav.popBackStack()
                }
            },
            onBack = {
                vm.stopPairing()
                nav.popBackStack()
            },
        )
    }
    composable(Routes.Profiles) {
        val state by stateRef
        val savedSettings by container.settings.values.collectAsState(initial = com.brasa.tv.data.storage.AppSettings())
        ProfileScreen(
            state,
            preferredProfileId = savedSettings.selectedProfileId,
            onLoad = { if (!state.previewMode) vm.loadProfiles {} },
            onSelect = { profile ->
                if (profile.hasPin && profile.kind != "kids") {
                    vm.prepareProfile(profile)
                    nav.navigate(Routes.ProfilePin)
                } else
                    vm.chooseProfile(profile) {
                        nav.navigate(Routes.Home) {
                            popUpTo(Routes.Profiles) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
            },
            onExit = onExit,
        )
    }
    composable(Routes.ProfilePin) {
        val state by stateRef
        ProfilePinScreen(
            state,
            onVerify = { pin ->
                vm.verifyPin(pin) { valid ->
                    if (valid)
                        state.profile?.let { profile ->
                            vm.chooseProfile(profile) {
                                nav.navigate(Routes.Home) {
                                    popUpTo(Routes.Profiles) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                }
            },
            onBack = { nav.popBackStack() },
        )
    }
}

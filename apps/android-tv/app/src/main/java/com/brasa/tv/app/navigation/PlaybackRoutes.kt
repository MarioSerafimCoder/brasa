package com.brasa.tv.app.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.app.BrasaViewModel
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.feature.details.DetailsScreen
import com.brasa.tv.feature.player.PlayerScreen
import com.brasa.tv.feature.profiles.*
import com.brasa.tv.feature.update.*

internal fun NavGraphBuilder.playbackRoutes(
    nav: NavHostController,
    vm: BrasaViewModel,
    stateRef: State<BrasaUiState>,
    container: AppContainer,
) {
    composable(Routes.Details) {
        val state by stateRef
        DetailsScreen(
            state,
            onPlay = { vm.loadPlayback(it) { nav.navigate(Routes.Player) } },
            onPlayFromStart = { vm.loadPlayback(it, true) { nav.navigate(Routes.Player) } },
            onPrefetch = vm::preloadPlayback,
            onCancelPreload = vm::cancelPreload,
            onFavorite = vm::toggleFavorite,
            onSignal = vm::signal,
            onBack = {
                vm.cancelPlaybackPreparation()
                nav.popBackStack()
            },
        )
    }
    composable(Routes.Player) {
        val state by stateRef
        PlayerScreen(
            state,
            container,
            onProgress = vm::saveProgress,
            onPlaybackFallback = vm::fallbackPlayback,
            onRemoteSeek = vm::seekPlayback,
            onConnectionProblem = vm::reportConnectionProblem,
            onRetry = { state.playbackItem?.let { vm.loadPlayback(it) {} } },
            onNext = { vm.loadPlayback(it) {} },
            onSignal = vm::signalPlayback,
            onBack = {
                vm.cancelPlaybackPreparation()
                nav.popBackStack()
            },
        )
    }
}

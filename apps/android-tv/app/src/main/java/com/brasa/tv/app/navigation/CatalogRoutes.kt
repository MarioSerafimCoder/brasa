package com.brasa.tv.app.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.app.BrasaViewModel
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.feature.home.CatalogScreen
import com.brasa.tv.feature.home.HomeScreen
import com.brasa.tv.feature.library.CollectionsScreen
import com.brasa.tv.feature.library.LibraryScreen
import com.brasa.tv.feature.profiles.*
import com.brasa.tv.feature.search.SearchScreen
import com.brasa.tv.feature.update.*

internal fun NavGraphBuilder.catalogRoutes(
    nav: NavHostController,
    vm: BrasaViewModel,
    stateRef: State<BrasaUiState>,
    container: AppContainer,
    updateVm: UpdateViewModel,
    updateStateRef: State<UpdateUiState>,
) {
    composable(Routes.Home) {
        val state by stateRef
        val updateState by updateStateRef
        LaunchedEffect(state.profile?.id) {
            if (!state.previewMode && state.profile != null) {
                vm.loadHome()
                vm.loadCatalog()
                if (updateState === UpdateUiState.Idle) updateVm.check(false)
            }
        }
        LaunchedEffect(updateState) {
            if (
                !state.previewMode &&
                    updateState is UpdateUiState.Available &&
                    nav.currentDestination?.route == Routes.Home
            )
                nav.navigate(Routes.Update)
        }
        HomeScreen(
            state,
            onItem = {
                vm.select(it)
                nav.navigate(Routes.Details)
            },
            onPlay = { item -> vm.loadPlayback(item) { nav.navigate(Routes.Player) } },
            onPrefetch = vm::prefetchPlaybackMetadata,
            onSearch = { nav.navigate(Routes.Search) },
            onMovies = { nav.navigate(Routes.Movies) },
            onSeries = { nav.navigate(Routes.Series) },
            onCollections = { nav.navigate(Routes.Collections) },
            onMyList = { nav.navigate(Routes.MyList) },
            onProfiles = { if (!state.previewMode) nav.navigate(Routes.Profiles) },
            onSettings = { if (!state.previewMode) nav.navigate(Routes.Settings) },
            onSeeMore = {
                vm.selectRow(it)
                nav.navigate(Routes.Catalog)
            },
            onRefresh = vm::refreshHome,
        )
    }
    composable(Routes.Catalog) {
        val state by stateRef
        CatalogScreen(
            state,
            onItem = {
                vm.select(it)
                nav.navigate(Routes.Details)
            },
            onBack = { nav.popBackStack() },
        )
    }
    composable(Routes.Movies) {
        val state by stateRef
        LibraryScreen(
            state,
            "movie",
            onItem = {
                vm.select(it)
                nav.navigate(Routes.Details)
            },
            onHome = { nav.navigate(Routes.Home) { popUpTo(Routes.Home) } },
            onMovies = {},
            onSeries = { nav.navigate(Routes.Series) },
            onCollections = { nav.navigate(Routes.Collections) },
            onSearch = { nav.navigate(Routes.Search) },
            onProfiles = { nav.navigate(Routes.Profiles) },
            onMyList = { nav.navigate(Routes.MyList) },
            onRefresh = vm::refreshCatalog,
        )
    }
    composable(Routes.Series) {
        val state by stateRef
        LibraryScreen(
            state,
            "series",
            onItem = {
                vm.select(it)
                nav.navigate(Routes.Details)
            },
            onHome = { nav.navigate(Routes.Home) { popUpTo(Routes.Home) } },
            onMovies = { nav.navigate(Routes.Movies) },
            onSeries = {},
            onCollections = { nav.navigate(Routes.Collections) },
            onSearch = { nav.navigate(Routes.Search) },
            onProfiles = { nav.navigate(Routes.Profiles) },
            onMyList = { nav.navigate(Routes.MyList) },
            onRefresh = vm::refreshCatalog,
        )
    }
    composable(Routes.MyList) {
        val state by stateRef
        LibraryScreen(
            state,
            "favorites",
            onItem = {
                vm.select(it)
                nav.navigate(Routes.Details)
            },
            onHome = { nav.navigate(Routes.Home) { popUpTo(Routes.Home) } },
            onMovies = { nav.navigate(Routes.Movies) },
            onSeries = { nav.navigate(Routes.Series) },
            onCollections = { nav.navigate(Routes.Collections) },
            onSearch = { nav.navigate(Routes.Search) },
            onProfiles = { nav.navigate(Routes.Profiles) },
            onMyList = {},
            onRefresh = vm::refreshCatalog,
        )
    }
    composable(Routes.Collections) {
        val state by stateRef
        CollectionsScreen(
            state,
            onItem = {
                vm.select(it)
                nav.navigate(Routes.Details)
            },
            onHome = { nav.navigate(Routes.Home) { popUpTo(Routes.Home) } },
            onMovies = { nav.navigate(Routes.Movies) },
            onSeries = { nav.navigate(Routes.Series) },
            onSearch = { nav.navigate(Routes.Search) },
            onProfiles = { nav.navigate(Routes.Profiles) },
            onMyList = { nav.navigate(Routes.MyList) },
            onRefresh = vm::refreshCatalog,
        )
    }
    composable(Routes.Search) {
        val state by stateRef
        SearchScreen(
            state,
            container.settings,
            onSearch = vm::search,
            onItem = {
                vm.select(it)
                nav.navigate(Routes.Details)
            },
            onMovies = { nav.navigate(Routes.Movies) },
            onSeries = { nav.navigate(Routes.Series) },
            onCollections = { nav.navigate(Routes.Collections) },
            onMyList = { nav.navigate(Routes.MyList) },
            onRefresh = vm::refreshCatalog,
            onBack = { nav.popBackStack() },
            onHome = {
                nav.navigate(Routes.Home) {
                    popUpTo(Routes.Home)
                    launchSingleTop = true
                }
            },
        )
    }
}

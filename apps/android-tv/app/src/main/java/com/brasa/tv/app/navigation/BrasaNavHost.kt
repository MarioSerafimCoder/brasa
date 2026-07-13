package com.brasa.tv.app.navigation
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.brasa.tv.app.BrasaViewModel
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.feature.details.DetailsScreen
import com.brasa.tv.feature.home.HomeScreen
import com.brasa.tv.feature.pairing.PairingScreen
import com.brasa.tv.feature.player.PlayerScreen
import com.brasa.tv.feature.profiles.*
import com.brasa.tv.feature.search.SearchScreen
import com.brasa.tv.feature.server.ServerScreen
import com.brasa.tv.feature.settings.SettingsScreen
import com.brasa.tv.feature.update.*
object Routes{const val Server="server";const val Pairing="pairing";const val Profiles="profiles";const val ProfilePin="profile-pin";const val Home="home";const val Search="search";const val Details="details";const val Player="player";const val Settings="settings";const val Update="update"}
@Composable fun BrasaNavHost(container:AppContainer){val nav=rememberNavController();val vm:BrasaViewModel=viewModel(factory=BrasaViewModel.Factory(container.repository));val updateVm:UpdateViewModel=viewModel(factory=UpdateViewModel.Factory(container.updateRepository,container.settings,container.updatePreferences,container.http,container.context,container.apkValidator,container.packageInstaller));val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){updateVm.permissionReturned()};val state by vm.state.collectAsState();val updateState by updateVm.state.collectAsState();NavHost(navController=nav,startDestination=Routes.Server){
composable(Routes.Server){LaunchedEffect(Unit){vm.restore{paired->if(paired)nav.navigate(Routes.Profiles){popUpTo(Routes.Server){inclusive=true}}}};ServerScreen(state,container){address->vm.connect(address){paired->nav.navigate(if(paired)Routes.Profiles else Routes.Pairing)}}}
composable(Routes.Pairing){PairingScreen(state,onStart={vm.startPairing(it){nav.navigate(Routes.Profiles){popUpTo(Routes.Pairing){inclusive=true}}}},onBack={vm.stopPairing();nav.popBackStack()})}
composable(Routes.Profiles){ProfileScreen(state,onLoad={vm.loadProfiles{}},onSelect={profile->if(profile.hasPin){vm.prepareProfile(profile);nav.navigate(Routes.ProfilePin)}else vm.chooseProfile(profile){nav.navigate(Routes.Home){popUpTo(Routes.Profiles){inclusive=true}}}})}
composable(Routes.ProfilePin){ProfilePinScreen(state,onVerify={pin->vm.verifyPin(pin){valid->if(valid)state.profile?.let{profile->vm.chooseProfile(profile){nav.navigate(Routes.Home){popUpTo(Routes.Profiles){inclusive=true}}}}}},onBack={nav.popBackStack()})}
composable(Routes.Home){LaunchedEffect(state.profile?.id){if(state.profile!=null&&updateState===UpdateUiState.Idle)updateVm.check(false)};LaunchedEffect(updateState){if(updateState is UpdateUiState.Available&&nav.currentDestination?.route==Routes.Home)nav.navigate(Routes.Update)};HomeScreen(state,onItem={vm.select(it);nav.navigate(Routes.Details)},onPlay={vm.select(it);vm.loadPlayback(it){nav.navigate(Routes.Player)}},onPrefetch=vm::prefetchPlayback,onSearch={nav.navigate(Routes.Search)},onProfiles={nav.navigate(Routes.Profiles)},onSettings={nav.navigate(Routes.Settings)},onRefresh=vm::refreshHome)}
composable(Routes.Search){SearchScreen(state,onSearch=vm::search,onItem={vm.select(it);nav.navigate(Routes.Details)},onBack={nav.popBackStack()})}
composable(Routes.Details){DetailsScreen(state,onPlay={vm.loadPlayback(it){nav.navigate(Routes.Player)}},onPrefetch=vm::prefetchPlayback,onFavorite=vm::toggleFavorite,onBack={nav.popBackStack()})}
composable(Routes.Player){PlayerScreen(state,container,onProgress=vm::saveProgress,onNext={vm.select(it);vm.loadPlayback(it){}},onBack={nav.popBackStack()})}
composable(Routes.Settings){SettingsScreen(state,lastUpdateCheckAt=updateVm.lastCheckAt(),onUpdates={updateVm.check(true);nav.navigate(Routes.Update)},onProfiles={nav.navigate(Routes.Profiles)},onForget={vm.forget{nav.navigate(Routes.Server){popUpTo(0)}}},onBack={nav.popBackStack()})}
composable(Routes.Update){UpdateScreen(updateState,onCheck={updateVm.check(true)},onDownload=updateVm::download,onCancel=updateVm::cancel,onInstall=updateVm::prepareInstall,onPermission={permissionLauncher.launch(updateVm.permissionIntent())},onBack={updateVm.defer();nav.popBackStack()})}
}}

package com.brasa.tv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.HomeResponse
import com.brasa.tv.core.model.HomeRow
import com.brasa.tv.core.model.PairingStatus
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.model.Profile
import com.brasa.tv.core.model.ServerInfo
import com.brasa.tv.core.model.WatchProgress
import com.brasa.tv.core.playback.PlaybackCoordinator
import com.brasa.tv.data.repository.BrasaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrasaUiState(
    val loading: Boolean = false,
    val message: String = "",
    val server: ServerInfo? = null,
    val pairing: PairingStatus? = null,
    val profiles: List<Profile> = emptyList(),
    val profile: Profile? = null,
    val home: HomeResponse? = null,
    val searchResults: List<CatalogItem> = emptyList(),
    val selected: CatalogItem? = null,
    val playback: PlaybackInfo? = null,
    val selectedRow: HomeRow? = null,
    val cacheBytes: Long = 0,
    val paired: Boolean = false,
    val previewMode: Boolean = false,
)

class BrasaViewModel(
    private val repository: BrasaRepository,
    private val playbackCoordinator: PlaybackCoordinator,
) : ViewModel() {
    private val mutable = MutableStateFlow(BrasaUiState())
    val state: StateFlow<BrasaUiState> = mutable.asStateFlow()
    private var pairingJob: Job? = null
    private var searchJob: Job? = null
    private var metadataPrefetchJob: Job? = null
    private var mediaPreloadJob: Job? = null

    fun enablePreview(page: String = "home") {
        mutable.value = PreviewCatalog.state(page)
    }

    fun restore(onReady: (Boolean) -> Unit) = launch {
        val info = repository.restore()
        mutable.value = mutable.value.copy(server = info, paired = info != null)
        onReady(info != null)
    }

    fun connect(address: String, onReady: (Boolean) -> Unit) = launch {
        val info = repository.connect(address)
        val paired = repository.isPaired()
        mutable.value = mutable.value.copy(server = info, paired = paired)
        onReady(paired)
    }

    fun startPairing(deviceName: String, onApproved: () -> Unit) {
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            setLoading(true)
            runCatching { repository.pair(deviceName) { status -> mutable.value = mutable.value.copy(pairing = status, loading = false) } }
                .onSuccess { mutable.value = mutable.value.copy(paired = true, loading = false); onApproved() }
                .onFailure(::error)
        }
    }

    fun stopPairing() { pairingJob?.cancel(); pairingJob = null }
    fun loadProfiles(onLoaded: (List<Profile>) -> Unit) = launch {
        if (mutable.value.previewMode) { onLoaded(mutable.value.profiles); return@launch }
        val profiles = repository.profiles(); mutable.value = mutable.value.copy(profiles = profiles); onLoaded(profiles)
    }
    fun prepareProfile(profile: Profile) { mutable.value = mutable.value.copy(profile = profile, message = "") }
    fun chooseProfile(profile: Profile, onReady: () -> Unit) = launch {
        cancelPreload()
        repository.selectProfile(profile.id)
        var opened = false
        repository.cachedHome(profile.id)?.let { mutable.value = mutable.value.copy(profile = profile, home = it); onReady(); opened = true }
        val home = repository.home(profile.id)
        mutable.value = mutable.value.copy(profile = profile, home = home)
        if (!opened) onReady()
    }
    fun refreshHome() = launch { val profile = mutable.value.profile ?: return@launch; mutable.value = mutable.value.copy(home = repository.home(profile.id)) }

    fun select(item: CatalogItem) {
        if (mutable.value.selected?.mediaKey != item.mediaKey) cancelPreload()
        mutable.value = mutable.value.copy(selected = item)
    }

    fun selectRow(row: HomeRow) { mutable.value = mutable.value.copy(selectedRow = row) }

    fun refreshCacheUsage() = viewModelScope.launch { mutable.value = mutable.value.copy(cacheBytes = playbackCoordinator.cacheSizeBytes()) }

    fun clearPlaybackCache() = viewModelScope.launch {
        mutable.value = mutable.value.copy(loading = true, message = "")
        playbackCoordinator.clearInactiveCache()
        mutable.value = mutable.value.copy(loading = false, cacheBytes = playbackCoordinator.cacheSizeBytes(), message = "Cache liberado com sucesso.")
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (mutable.value.previewMode) {
            mutable.value = mutable.value.copy(searchResults = PreviewCatalog.search(query))
            return
        }
        searchJob = viewModelScope.launch {
            delay(150)
            val profile = mutable.value.profile ?: return@launch
            runCatching { repository.search(profile.id, query) }.onSuccess { mutable.value = mutable.value.copy(searchResults = it) }.onFailure(::error)
        }
    }

    fun toggleFavorite() = launch {
        val profile = mutable.value.profile ?: return@launch
        val item = mutable.value.selected ?: return@launch
        val next = !item.favorite
        mutable.value = mutable.value.copy(selected = item.copy(favorite = next))
        if (mutable.value.previewMode) return@launch
        runCatching { repository.favorite(profile.id, item.mediaKey, next) }.onFailure { mutable.value = mutable.value.copy(selected = item); error(it) }
        refreshHome()
    }

    fun loadPlayback(item: CatalogItem, onReady: () -> Unit) = launch {
        metadataPrefetchJob?.cancel()
        mediaPreloadJob?.cancel()
        if (mutable.value.previewMode) {
            mutable.value = mutable.value.copy(selected = item, playback = PreviewCatalog.playback(item))
            onReady()
            return@launch
        }
        val profile = mutable.value.profile ?: return@launch
        val playback = repository.playback(profile.id, item.mediaKey)
        mutable.value = mutable.value.copy(playback = playback)
        onReady()
    }

    fun prefetchPlaybackMetadata(item: CatalogItem) {
        if (mutable.value.previewMode) return
        if (item.type == "series") return
        metadataPrefetchJob?.cancel()
        val profile = mutable.value.profile ?: return
        metadataPrefetchJob = viewModelScope.launch {
            delay(250)
            repository.prefetchPlayback(profile.id, item.mediaKey)
        }
    }

    fun preloadPlayback(item: CatalogItem) {
        if (mutable.value.previewMode) return
        if (item.type == "series") return
        mediaPreloadJob?.cancel()
        playbackCoordinator.cancelPreload()
        val profile = mutable.value.profile ?: return
        mediaPreloadJob = viewModelScope.launch {
            delay(650)
            val info = repository.playback(profile.id, item.mediaKey)
            val selected = mutable.value.selected ?: return@launch
            if (selected.mediaKey != item.mediaKey && selected.type != "series") return@launch
            playbackCoordinator.preload(repository.serverBaseUrl(), info)
        }
    }

    fun cancelPreload() {
        mediaPreloadJob?.cancel()
        mediaPreloadJob = null
        playbackCoordinator.cancelPreload()
    }

    fun saveProgress(progress: WatchProgress) = viewModelScope.launch {
        if (mutable.value.previewMode) return@launch
        val profile = mutable.value.profile ?: return@launch
        val item = mutable.value.selected ?: return@launch
        runCatching { repository.saveProgress(profile.id, item.mediaKey, progress) }
    }

    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) = launch {
        val profile = mutable.value.profile ?: return@launch
        val valid = if (mutable.value.previewMode) pin == "1234" else repository.verifyPin(profile.id, pin)
        mutable.value = mutable.value.copy(message = if (valid) "" else "PIN incorreto. Tente novamente.")
        onResult(valid)
    }
    fun forget(onDone: () -> Unit) = launch { cancelPreload(); playbackCoordinator.clear(); repository.forget(); mutable.value = BrasaUiState(); onDone() }

    override fun onCleared() {
        pairingJob?.cancel(); searchJob?.cancel(); metadataPrefetchJob?.cancel(); cancelPreload()
        super.onCleared()
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { setLoading(true); runCatching { block() }.onFailure(::error); setLoading(false) }
    private fun setLoading(value: Boolean) { mutable.value = mutable.value.copy(loading = value, message = if (value) "" else mutable.value.message) }
    private fun error(value: Throwable) { mutable.value = mutable.value.copy(loading = false, message = value.message ?: "Não foi possível conectar ao BRasa.") }

    class Factory(
        private val repository: BrasaRepository,
        private val playbackCoordinator: PlaybackCoordinator,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BrasaViewModel(repository, playbackCoordinator) as T
        }
    }
}

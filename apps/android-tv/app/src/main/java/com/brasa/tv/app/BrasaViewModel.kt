package com.brasa.tv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.CatalogResponse
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
import kotlinx.coroutines.CancellationException
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
    val catalog: CatalogResponse? = null,
    val searchResults: List<CatalogItem> = emptyList(),
    val selected: CatalogItem? = null,
    val playback: PlaybackInfo? = null,
    val selectedRow: HomeRow? = null,
    val cacheBytes: Long = 0,
    val paired: Boolean = false,
    val previewMode: Boolean = false,
    val libraryScanning: Boolean = false,
    val libraryScanMessage: String = "",
    val reconnecting: Boolean = false,
    val searching: Boolean = false,
    val searchError: String = "",
)

class BrasaViewModel(
    private val repository: BrasaRepository,
    private val playbackCoordinator: PlaybackCoordinator,
) : ViewModel() {
    private val mutable = MutableStateFlow(BrasaUiState())
    val state: StateFlow<BrasaUiState> = mutable.asStateFlow()
    private var pairingJob: Job? = null
    private var libraryScanJob: Job? = null
    private var searchJob: Job? = null
    private var metadataPrefetchJob: Job? = null
    private var mediaPreloadJob: Job? = null
    private var playbackPreparationJob: Job? = null
    private var playbackGeneration = 0L
    private var preloadMediaKey: String = ""

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
    fun chooseProfile(profile: Profile, onReady: () -> Unit) {
        cancelPreload()
        cancelPlaybackPreparation()
        mutable.value = mutable.value.copy(
            profile = profile,
            home = null,
            catalog = null,
            searchResults = emptyList(),
            selected = null,
            selectedRow = null,
            message = "",
        )
        onReady()
        viewModelScope.launch {
            runCatching { repository.selectProfile(profile.id) }.onFailure(::error)
        }
    }
    fun refreshHome() = launch {
        val profile = mutable.value.profile ?: return@launch
        val home = repository.home(profile.id)
        if (mutable.value.profile?.id == profile.id) mutable.value = mutable.value.copy(home = home)
    }
    fun loadHome() {
        val profile = mutable.value.profile ?: return
        viewModelScope.launch {
            if (mutable.value.home == null) repository.cachedHome(profile.id)?.let { cached ->
                if (mutable.value.profile?.id == profile.id) mutable.value = mutable.value.copy(home = cached, reconnecting = true)
            }
            runCatching { repository.home(profile.id) }
                .onSuccess { home -> if (mutable.value.profile?.id == profile.id) mutable.value = mutable.value.copy(home = home, reconnecting = false, message = "") }
                .onFailure { failure -> if (mutable.value.profile?.id == profile.id) mutable.value = mutable.value.copy(reconnecting = false, message = failure.message ?: "Não foi possível atualizar a página inicial.") }
        }
    }
    fun refreshCatalog() = launch {
        val profile = mutable.value.profile ?: return@launch
        val catalog = repository.catalog(profile.id)
        if (mutable.value.profile?.id == profile.id) mutable.value = mutable.value.copy(catalog = catalog)
    }

    fun scanLibrary() {
        if (libraryScanJob?.isActive == true || !mutable.value.paired || mutable.value.previewMode) return
        libraryScanJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(libraryScanning = true, libraryScanMessage = "Solicitando busca ao computador…")
            try {
                repository.scanLibrary { status ->
                    val progress = if (status.state == "syncing" && status.progress > 0) " (${status.progress.coerceIn(0, 99)}%)" else ""
                    mutable.value = mutable.value.copy(libraryScanMessage = status.message + progress)
                }
                mutable.value = mutable.value.copy(libraryScanMessage = "Carregando catálogo atualizado…")
                val profile = mutable.value.profile
                if (profile != null) {
                    val catalog = repository.catalog(profile.id)
                    val home = repository.home(profile.id)
                    if (mutable.value.profile?.id == profile.id) {
                        val items = catalog.movies + catalog.series + catalog.series.flatMap { it.seasons.flatMap { season -> season.episodes } }
                        mutable.value = mutable.value.copy(catalog = catalog, home = home,
                            selected = items.find { it.mediaKey == mutable.value.selected?.mediaKey },
                            selectedRow = null, searchResults = emptyList())
                    }
                }
                mutable.value = mutable.value.copy(libraryScanMessage = "Busca concluída. Os títulos disponíveis já foram atualizados.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(libraryScanMessage = error.message ?: "Não foi possível buscar novos títulos. Tente novamente.")
            } finally {
                mutable.value = mutable.value.copy(libraryScanning = false)
            }
        }
    }

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
        mutable.value = mutable.value.copy(searching = query.isNotBlank(), searchError = "")
        searchJob = viewModelScope.launch {
            delay(150)
            val profile = mutable.value.profile ?: return@launch
            runCatching { repository.search(profile.id, query) }
                .onSuccess { mutable.value = mutable.value.copy(searchResults = it, searching = false) }
                .onFailure { mutable.value = mutable.value.copy(searching = false, searchError = it.message ?: "Não foi possível buscar agora.") }
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
        refreshCatalog()
    }

    fun signal(action: String, enabled: Boolean = true) = launch {
        val profile = mutable.value.profile ?: return@launch
        val item = mutable.value.selected ?: return@launch
        if (!mutable.value.previewMode) repository.signal(profile.id, item.mediaKey, action, enabled)
        val reaction = when { !enabled && action in setOf("like", "not-for-me") -> ""; action in setOf("like", "not-for-me") -> action; else -> item.reaction }
        mutable.value = mutable.value.copy(selected = item.copy(reaction = reaction, hiddenSuggestion = if (action == "hide") enabled else item.hiddenSuggestion), message = when (action) { "hide" -> if(enabled) "Sugestão ocultada. Você pode desfazer nos detalhes." else "Sugestão restaurada."; "dismiss-continue" -> "Removido de Continuar assistindo."; "mark-watched" -> "Marcado como assistido."; else -> "Preferência salva." })
        refreshHome()
        refreshCatalog()
    }

    fun resetPersonalization() = launch {
        val profile = mutable.value.profile ?: return@launch
        if (!mutable.value.previewMode) repository.resetPersonalization(profile.id)
        mutable.value = mutable.value.copy(message = "Personalização reiniciada. Favoritos e progresso foram preservados.")
        refreshHome()
    }

    fun saveAutoplayNext(enabled: Boolean) = viewModelScope.launch {
        val profile = mutable.value.profile ?: return@launch
        runCatching { repository.savePreferences(profile.id, enabled) }
    }

    fun loadPlayback(item: CatalogItem, fromBeginning: Boolean = false, onReady: () -> Unit) = launch {
        metadataPrefetchJob?.cancel()
        mediaPreloadJob?.cancel()
        cancelPlaybackPreparation()
        val requestGeneration = playbackGeneration
        mutable.value = mutable.value.copy(playback = null, message = "")
        if (mutable.value.previewMode) {
            mutable.value = mutable.value.copy(selected = item, playback = PreviewCatalog.playback(item))
            onReady()
            return@launch
        }
        val profile = mutable.value.profile ?: return@launch
        if (fromBeginning) repository.saveProgress(profile.id, item.mediaKey, WatchProgress(mediaType = if (item.type == "episode") "episode" else "movie", mediaId = item.id, seriesId = item.seriesId, duration = item.progress?.duration ?: 0.0))
        val playback = repository.playback(profile.id, item.mediaKey, forceRefresh = true, positionMs = if (fromBeginning) 0 else null)
        if (requestGeneration != playbackGeneration || mutable.value.profile?.id != profile.id) return@launch
        mutable.value = mutable.value.copy(selected = item, playback = playback)
        onReady()
        if (playback.preparationStatus !in setOf("ready", "failed")) playbackPreparationJob = viewModelScope.launch poll@ {
            repeat(MAX_PREPARATION_POLLS) {
                delay(1_000)
                val next = runCatching { repository.playback(profile.id, item.mediaKey, forceRefresh = true, positionMs = playback.playbackOffset + playback.resumePosition) }.getOrElse {
                    if (it is CancellationException) throw it
                    if (requestGeneration != playbackGeneration) return@poll
                    mutable.value = mutable.value.copy(playback = mutable.value.playback?.copy(preparationStatus = "failed", errorType = "network", errorMessage = "Não foi possível receber os dados do servidor."))
                    return@poll
                }
                if (requestGeneration != playbackGeneration || mutable.value.selected?.mediaKey != item.mediaKey) return@poll
                mutable.value = mutable.value.copy(playback = next)
                if (next.preparationStatus in setOf("ready", "failed")) return@poll
            }
            if (mutable.value.selected?.mediaKey == item.mediaKey) mutable.value = mutable.value.copy(playback = mutable.value.playback?.copy(preparationStatus = "failed", errorType = "timeout", errorMessage = "O servidor demorou demais para preparar esta mídia. Tente novamente."))
        }
    }

    fun fallbackPlayback(mediaKey: String) {
        if (mutable.value.previewMode || mutable.value.playback?.mediaKey != mediaKey) return
        val profile = mutable.value.profile ?: return
        playbackPreparationJob?.cancel()
        mutable.value = mutable.value.copy(playback = mutable.value.playback?.copy(playbackUrl = "", preparationStatus = "preparing", preparationProgress = 0.0, playbackMode = "hls", errorType = "", errorMessage = ""))
        playbackPreparationJob = viewModelScope.launch poll@ {
            repeat(MAX_PREPARATION_POLLS) {
                val next = runCatching { repository.playback(profile.id, mediaKey, forceRefresh = true, fallbackMode = "transcode") }.getOrElse {
                    if (it is CancellationException) throw it
                    if (mutable.value.playback?.mediaKey == mediaKey) mutable.value = mutable.value.copy(playback = mutable.value.playback?.copy(preparationStatus = "failed", errorType = "network", errorMessage = "Não foi possível preparar a versão compatível."))
                    return@poll
                }
                if (mutable.value.playback?.mediaKey != mediaKey) return@poll
                mutable.value = mutable.value.copy(playback = next)
                if (next.preparationStatus in setOf("ready", "failed")) return@poll
                delay(1_000)
            }
            if (mutable.value.playback?.mediaKey == mediaKey) mutable.value = mutable.value.copy(playback = mutable.value.playback?.copy(preparationStatus = "failed", errorType = "timeout", errorMessage = "O servidor demorou demais para preparar a versão compatível."))
        }
    }

    fun seekPlayback(mediaKey: String, positionMs: Long) {
        if (mutable.value.previewMode || mutable.value.playback?.mediaKey != mediaKey) return
        val profile = mutable.value.profile ?: return
        val target = positionMs.coerceAtLeast(0)
        cancelPlaybackPreparation()
        val requestGeneration = playbackGeneration
        mutable.value = mutable.value.copy(playback = mutable.value.playback?.copy(playbackUrl = "", preparationStatus = "preparing", preparationProgress = 0.0, playbackMode = "hls", errorType = "", errorMessage = ""))
        playbackPreparationJob = viewModelScope.launch poll@ {
            repeat(MAX_PREPARATION_POLLS) {
                val next = runCatching {
                    repository.playback(
                        profile.id,
                        mediaKey,
                        forceRefresh = true,
                        fallbackMode = "transcode",
                        positionMs = target,
                    )
                }.getOrElse {
                    if (it is CancellationException) throw it
                    if (requestGeneration != playbackGeneration) return@poll
                    if (mutable.value.playback?.mediaKey == mediaKey) mutable.value = mutable.value.copy(playback = mutable.value.playback?.copy(preparationStatus = "failed", errorType = "network", errorMessage = "Não foi possível carregar o ponto escolhido."))
                    return@poll
                }
                if (requestGeneration != playbackGeneration || mutable.value.playback?.mediaKey != mediaKey) return@poll
                mutable.value = mutable.value.copy(playback = next)
                if (next.preparationStatus in setOf("ready", "failed")) return@poll
                delay(1_000)
            }
            if (mutable.value.playback?.mediaKey == mediaKey) mutable.value = mutable.value.copy(playback = mutable.value.playback?.copy(preparationStatus = "failed", errorType = "timeout", errorMessage = "O servidor demorou demais para carregar o ponto escolhido."))
        }
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
        preloadMediaKey = item.mediaKey
        mediaPreloadJob = viewModelScope.launch {
            delay(650)
            val info = repository.playback(profile.id, item.mediaKey, prepare = false)
            if (preloadMediaKey != item.mediaKey) return@launch
            val selected = mutable.value.selected ?: return@launch
            if (selected.mediaKey != item.mediaKey && selected.type != "series") return@launch
            if (info.preparationStatus == "ready" && info.playbackUrl.isNotBlank() && info.playbackMode != "hls") playbackCoordinator.preload(repository.serverBaseUrl(), info)
        }
    }

    fun cancelPreload() {
        mediaPreloadJob?.cancel()
        mediaPreloadJob = null
        preloadMediaKey = ""
        playbackCoordinator.cancelPreload()
    }

    fun cancelPlaybackPreparation() {
        playbackGeneration++
        playbackPreparationJob?.cancel()
        playbackPreparationJob = null
    }

    fun saveProgress(mediaKey: String, progress: WatchProgress) = viewModelScope.launch {
        if (mutable.value.previewMode) return@launch
        val profile = mutable.value.profile ?: return@launch
        runCatching { repository.saveProgress(profile.id, mediaKey, progress) }
    }

    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) = launch {
        val profile = mutable.value.profile ?: return@launch
        val valid = if (mutable.value.previewMode) pin == "1234" else repository.verifyPin(profile.id, pin)
        mutable.value = mutable.value.copy(message = if (valid) "" else "PIN incorreto. Tente novamente.")
        onResult(valid)
    }
    fun forget(onDone: () -> Unit) = launch { libraryScanJob?.cancel(); cancelPreload(); playbackCoordinator.clear(); repository.forget(); mutable.value = BrasaUiState(); onDone() }

    override fun onCleared() {
        pairingJob?.cancel(); searchJob?.cancel(); metadataPrefetchJob?.cancel(); playbackPreparationJob?.cancel(); cancelPreload()
        super.onCleared()
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { setLoading(true); runCatching { block() }.onFailure(::error); setLoading(false) }
    private fun setLoading(value: Boolean) { mutable.value = mutable.value.copy(loading = value, message = if (value) "" else mutable.value.message) }
    private fun error(value: Throwable) { mutable.value = mutable.value.copy(loading = false, message = value.message ?: "Não foi possível conectar ao BRasa.") }

    private companion object { const val MAX_PREPARATION_POLLS = 600 }

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

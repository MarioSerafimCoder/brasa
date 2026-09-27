package com.brasa.tv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brasa.tv.app.OperationDomain.*
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.HomeRow
import com.brasa.tv.core.model.Profile
import com.brasa.tv.core.model.WatchProgress
import com.brasa.tv.core.playback.PlaybackCoordinator
import com.brasa.tv.data.repository.BrasaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BrasaViewModel(
    private val repository: BrasaRepository,
    private val playbackCoordinator: PlaybackCoordinator,
) : ViewModel() {
    private val mutable = MutableStateFlow(BrasaUiState())
    val state: StateFlow<BrasaUiState> = mutable.asStateFlow()
    private val operations = OperationTracker {
        mutable.value = mutable.value.copy(operations = it)
    }
    private val favoriteMutex = Mutex()
    private var homeJob: Job? = null
    private var catalogJob: Job? = null
    private var pairingJob: Job? = null
    private var libraryScanJob: Job? = null
    private var searchJob: Job? = null
    private var metadataPrefetchJob: Job? = null
    private var mediaPreloadJob: Job? = null
    private var playbackPreparationJob: Job? = null
    private var playbackRequestJob: Job? = null
    private var playbackGeneration = 0L
    private var preloadMediaKey: String = ""

    fun enablePreview(page: String = "home") {
        mutable.value = PreviewCatalog.state(page)
    }

    fun restore(onReady: (Boolean) -> Unit) =
        launch(Session) {
            val info = repository.restore()
            mutable.value =
                mutable.value.copy(server = info, paired = info != null, connectionProblem = null)
            onReady(info != null)
        }

    fun connect(address: String, onReady: (Boolean) -> Unit) =
        launch(Session) {
            val info = repository.connect(address)
            val paired = repository.isPaired()
            mutable.value =
                mutable.value.copy(server = info, paired = paired, connectionProblem = null)
            onReady(paired)
        }

    fun startPairing(deviceName: String, onApproved: () -> Unit) {
        pairingJob?.cancel()
        pairingJob =
            launch(Session) {
                repository.pair(deviceName) { status ->
                    mutable.value = mutable.value.copy(pairing = status)
                }
                mutable.value = mutable.value.copy(paired = true, connectionProblem = null)
                onApproved()
            }
    }

    fun stopPairing() {
        pairingJob?.cancel()
        pairingJob = null
    }

    fun loadProfiles(onLoaded: (List<Profile>) -> Unit) =
        launch(Session) {
            if (mutable.value.previewMode) {
                onLoaded(mutable.value.profiles)
                return@launch
            }
            val profiles = repository.profiles()
            mutable.value = mutable.value.copy(profiles = profiles)
            onLoaded(profiles)
        }

    fun prepareProfile(profile: Profile) {
        operations.message(Session, "")
        mutable.value = mutable.value.copy(profile = profile)
    }

    fun chooseProfile(profile: Profile, onReady: () -> Unit) {
        homeJob?.cancel()
        catalogJob?.cancel()
        searchJob?.cancel()
        operations.reset()
        cancelPreload()
        cancelPlaybackPreparation()
        mutable.value =
            mutable.value.copy(
                profile = profile,
                home = null,
                catalog = null,
                searchResults = emptyList(),
                selected = null,
                playbackItem = null,
                playback = null,
                selectedRow = null,
                searching = false,
                searchError = "",
            )
        onReady()
        viewModelScope.launch {
            runCatching { repository.selectProfile(profile.id) }
                .onFailure { if (mutable.value.profile?.id == profile.id) error(it, Session) }
        }
    }

    fun refreshHome() {
        loadHome(force = true)
    }

    fun loadHome(force: Boolean = false) {
        val profile = mutable.value.profile ?: return
        if (
            mutable.value.previewMode ||
                (!force && (homeJob?.isActive == true || mutable.value.home != null))
        )
            return
        homeJob?.cancel()
        homeJob =
            launch(Home) {
                if (mutable.value.home == null)
                    repository.cachedHome(profile.id)?.let { cached ->
                        if (mutable.value.profile?.id == profile.id)
                            mutable.value = mutable.value.copy(home = cached, reconnecting = true)
                    }
                try {
                    val home = repository.home(profile.id)
                    if (mutable.value.profile?.id == profile.id)
                        mutable.value = mutable.value.copy(home = home, connectionProblem = null)
                } finally {
                    if (mutable.value.profile?.id == profile.id)
                        mutable.value = mutable.value.copy(reconnecting = false)
                }
            }
    }

    fun refreshCatalog() {
        loadCatalog(force = true)
    }

    fun loadCatalog(force: Boolean = false) {
        val profile = mutable.value.profile ?: return
        if (
            mutable.value.previewMode ||
                (!force && (catalogJob?.isActive == true || mutable.value.catalog != null))
        )
            return
        catalogJob?.cancel()
        catalogJob =
            launch(Catalog) {
                val catalog = repository.catalog(profile.id)
                if (mutable.value.profile?.id == profile.id)
                    mutable.value = mutable.value.copy(catalog = catalog, connectionProblem = null)
            }
    }

    fun scanLibrary() {
        if (libraryScanJob?.isActive == true || !mutable.value.paired || mutable.value.previewMode)
            return
        libraryScanJob =
            viewModelScope.launch {
                mutable.value =
                    mutable.value.copy(
                        libraryScanning = true,
                        libraryScanMessage = "Solicitando busca ao computador…",
                    )
                try {
                    repository.scanLibrary { status ->
                        val progress =
                            if (status.state == "syncing" && status.progress > 0)
                                " (${status.progress.coerceIn(0, 99)}%)"
                            else ""
                        mutable.value =
                            mutable.value.copy(libraryScanMessage = status.message + progress)
                    }
                    mutable.value =
                        mutable.value.copy(libraryScanMessage = "Carregando catálogo atualizado…")
                    val profile = mutable.value.profile
                    if (profile != null) {
                        val catalog = repository.catalog(profile.id)
                        val home = repository.home(profile.id)
                        if (mutable.value.profile?.id == profile.id) {
                            val items =
                                catalog.movies +
                                    catalog.series +
                                    catalog.series.flatMap {
                                        it.seasons.flatMap { season -> season.episodes }
                                    }
                            mutable.value =
                                mutable.value.copy(
                                    catalog = catalog,
                                    home = home,
                                    selected =
                                        items.find {
                                            it.mediaKey == mutable.value.selected?.mediaKey
                                        },
                                    selectedRow = null,
                                    searchResults = emptyList(),
                                )
                        }
                    }
                    mutable.value =
                        mutable.value.copy(
                            libraryScanMessage =
                                "Busca concluída. Os títulos disponíveis já foram atualizados."
                        )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutable.value =
                        mutable.value.copy(
                            libraryScanMessage =
                                error.message
                                    ?: "Não foi possível buscar novos títulos. Tente novamente."
                        )
                } finally {
                    mutable.value = mutable.value.copy(libraryScanning = false)
                }
            }
    }

    fun select(item: CatalogItem) {
        if (mutable.value.selected?.mediaKey != item.mediaKey) cancelPreload()
        mutable.value = mutable.value.copy(selected = item)
    }

    fun selectRow(row: HomeRow) {
        mutable.value = mutable.value.copy(selectedRow = row)
    }

    fun refreshCacheUsage() =
        launch(Cache) {
            mutable.value = mutable.value.copy(cacheBytes = playbackCoordinator.cacheSizeBytes())
        }

    fun clearPlaybackCache() =
        launch(Cache) {
            playbackCoordinator.clearInactiveCache()
            mutable.value = mutable.value.copy(cacheBytes = playbackCoordinator.cacheSizeBytes())
            operations.message(Cache, "Cache liberado com sucesso.")
        }

    fun search(query: String) {
        searchJob?.cancel()
        if (mutable.value.previewMode) {
            mutable.value = mutable.value.copy(searchResults = PreviewCatalog.search(query))
            return
        }
        mutable.value = mutable.value.copy(searching = query.isNotBlank(), searchError = "")
        searchJob =
            viewModelScope.launch {
                delay(150)
                val profile = mutable.value.profile ?: return@launch
                runCatching { repository.search(profile.id, query) }
                    .onSuccess {
                        ensureActive()
                        mutable.value =
                            mutable.value.copy(
                                searchResults = it,
                                searching = false,
                                searchError = "",
                            )
                    }
                    .onFailure {
                        ensureActive()
                        if (it is CancellationException) throw it
                        mutable.value =
                            mutable.value.copy(
                                searching = false,
                                searchError = "Não foi possível buscar agora. Tente novamente.",
                            )
                    }
            }
    }

    fun toggleFavorite() {
        val profile = mutable.value.profile ?: return
        val mediaKey = mutable.value.selected?.mediaKey ?: return
        launch(Personalization) {
            favoriteMutex.withLock {
                if (mutable.value.profile?.id != profile.id) return@withLock
                val item =
                    mutable.value.selected?.takeIf { it.mediaKey == mediaKey } ?: return@withLock
                val next = !item.favorite
                homeJob?.cancel()
                catalogJob?.cancel()
                mutable.value = mutable.value.withFavorite(mediaKey, next)
                try {
                    if (!mutable.value.previewMode) repository.favorite(profile.id, mediaKey, next)
                } catch (failure: Exception) {
                    if (mutable.value.profile?.id == profile.id)
                        mutable.value = mutable.value.withFavorite(mediaKey, item.favorite)
                    throw failure
                }
                // Catalog membership is already updated locally; only home rows need reranking.
                if (mutable.value.profile?.id == profile.id) refreshHome()
            }
        }
    }

    fun signal(action: String, enabled: Boolean = true) =
        signalItem(mutable.value.selected, action, enabled)

    fun signalPlayback(action: String, enabled: Boolean = true) =
        signalItem(mutable.value.playbackItem, action, enabled)

    private fun signalItem(target: CatalogItem?, action: String, enabled: Boolean) =
        launch(Personalization) {
            val profile = mutable.value.profile ?: return@launch
            val item = target ?: return@launch
            if (!mutable.value.previewMode)
                repository.signal(profile.id, item.mediaKey, action, enabled)
            if (mutable.value.profile?.id != profile.id) return@launch
            val reaction =
                when {
                    !enabled && action in setOf("like", "not-for-me") -> ""
                    action in setOf("like", "not-for-me") -> action
                    else -> item.reaction
                }
            val updated =
                item.copy(
                    reaction = reaction,
                    hiddenSuggestion = if (action == "hide") enabled else item.hiddenSuggestion,
                )
            mutable.value =
                mutable.value.copy(
                    selected =
                        if (mutable.value.selected?.mediaKey == item.mediaKey) updated
                        else mutable.value.selected,
                    playbackItem =
                        if (mutable.value.playbackItem?.mediaKey == item.mediaKey) updated
                        else mutable.value.playbackItem,
                )
            operations.message(
                Personalization,
                when (action) {
                    "hide" ->
                        if (enabled) "Sugestão ocultada. Você pode desfazer nos detalhes."
                        else "Sugestão restaurada."
                    "dismiss-continue" -> "Removido de Continuar assistindo."
                    "mark-watched" -> "Marcado como assistido."
                    else -> "Preferência salva."
                },
            )
            refreshHome()
            refreshCatalog()
        }

    fun resetPersonalization() =
        launch(Personalization) {
            val profile = mutable.value.profile ?: return@launch
            if (!mutable.value.previewMode) repository.resetPersonalization(profile.id)
            operations.message(
                Personalization,
                "Personalização reiniciada. Favoritos e progresso foram preservados.",
            )
            refreshHome()
        }

    fun saveAutoplayNext(enabled: Boolean) =
        viewModelScope.launch {
            val profile = mutable.value.profile ?: return@launch
            runCatching { repository.savePreferences(profile.id, enabled) }
        }

    fun loadPlayback(item: CatalogItem, fromBeginning: Boolean = false, onReady: () -> Unit) {
        playbackRequestJob?.cancel()
        playbackRequestJob =
            launch(Playback) {
                metadataPrefetchJob?.cancel()
                mediaPreloadJob?.cancel()
                cancelPlaybackPreparation(cancelRequest = false)
                val requestGeneration = playbackGeneration
                mutable.value = mutable.value.beginPlayback(item, fromBeginning)
                if (mutable.value.previewMode) {
                    mutable.value = mutable.value.copy(playback = PreviewCatalog.playback(item))
                    onReady()
                    return@launch
                }
                val profile = mutable.value.profile ?: return@launch
                if (fromBeginning)
                    repository.saveProgress(
                        profile.id,
                        item.mediaKey,
                        WatchProgress(
                            mediaType = if (item.type == "episode") "episode" else "movie",
                            mediaId = item.id,
                            seriesId = item.seriesId,
                            duration = item.progress?.duration ?: 0.0,
                        ),
                    )
                val playback =
                    repository.playback(
                        profile.id,
                        item.mediaKey,
                        forceRefresh = true,
                        positionMs = if (fromBeginning) 0 else null,
                    )
                if (
                    requestGeneration != playbackGeneration ||
                        mutable.value.profile?.id != profile.id
                )
                    return@launch
                mutable.value = mutable.value.copy(playback = playback)
                onReady()
                if (playback.preparationStatus !in setOf("ready", "failed"))
                    pollPlayback(
                        profile.id,
                        item.mediaKey,
                        positionMs = playback.playbackOffset + playback.resumePosition,
                        waitBeforeFirst = true,
                        timeoutMessage =
                            "O servidor demorou demais para preparar esta mídia. Tente novamente.",
                    )
            }
    }

    fun fallbackPlayback(mediaKey: String) {
        if (mutable.value.previewMode || mutable.value.playback?.mediaKey != mediaKey) return
        val profile = mutable.value.profile ?: return
        playbackPreparationJob?.cancel()
        mutable.value =
            mutable.value.copy(
                playback =
                    mutable.value.playback?.copy(
                        playbackUrl = "",
                        preparationStatus = "preparing",
                        preparationProgress = 0.0,
                        playbackMode = "hls",
                        errorType = "",
                        errorMessage = "",
                    )
            )
        pollPlayback(
            profile.id,
            mediaKey,
            forceConversion = true,
            timeoutMessage = "O servidor demorou demais para preparar a versão compatível.",
        )
    }

    fun seekPlayback(mediaKey: String, positionMs: Long, forceConversion: Boolean) {
        if (mutable.value.previewMode || mutable.value.playback?.mediaKey != mediaKey) return
        val profile = mutable.value.profile ?: return
        val target = positionMs.coerceAtLeast(0)
        val convert =
            forceConversion ||
                (mutable.value.playback?.let { it.playbackMode == "hls" && !it.videoCopied } ==
                    true)
        cancelPlaybackPreparation()
        mutable.value =
            mutable.value.copy(
                playback =
                    mutable.value.playback?.copy(
                        playbackUrl = "",
                        preparationStatus = "preparing",
                        preparationProgress = 0.0,
                        playbackMode = "hls",
                        errorType = "",
                        errorMessage = "",
                    )
            )
        pollPlayback(
            profile.id,
            mediaKey,
            positionMs = target,
            forceConversion = convert,
            timeoutMessage = "O servidor demorou demais para carregar o ponto escolhido.",
        )
    }

    private fun pollPlayback(
        profileId: String,
        mediaKey: String,
        positionMs: Long? = null,
        forceConversion: Boolean = false,
        waitBeforeFirst: Boolean = false,
        timeoutMessage: String,
    ) {
        playbackPreparationJob?.cancel()
        val requestGeneration = playbackGeneration
        fun isCurrent() =
            requestGeneration == playbackGeneration &&
                mutable.value.profile?.id == profileId &&
                mutable.value.playbackItem?.mediaKey == mediaKey
        fun fail(type: String, message: String) {
            if (isCurrent())
                mutable.value =
                    mutable.value.copy(
                        playback =
                            mutable.value.playback?.copy(
                                preparationStatus = "failed",
                                errorType = type,
                                errorMessage = message,
                            )
                    )
        }
        playbackPreparationJob =
            viewModelScope.launch {
                try {
                    val finished =
                        awaitPlaybackPreparation(
                            waitBeforeFirst,
                            load = {
                                repository.playback(
                                    profileId,
                                    mediaKey,
                                    forceRefresh = true,
                                    fallbackMode = if (forceConversion) "transcode" else "",
                                    positionMs = positionMs,
                                )
                            },
                            publish = { next ->
                                if (isCurrent()) mutable.value = mutable.value.copy(playback = next)
                            },
                        )
                    if (!finished) fail("timeout", timeoutMessage)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    ensureActive()
                    if (isCurrent()) {
                        fail("network", repository.problem(failure).explanation)
                        error(failure, Playback)
                    }
                }
            }
    }

    fun prefetchPlaybackMetadata(item: CatalogItem) {
        if (mutable.value.previewMode) return
        if (item.type == "series") return
        metadataPrefetchJob?.cancel()
        val profile = mutable.value.profile ?: return
        metadataPrefetchJob =
            viewModelScope.launch {
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
        mediaPreloadJob =
            viewModelScope.launch {
                delay(650)
                val info = repository.playback(profile.id, item.mediaKey, prepare = false)
                if (preloadMediaKey != item.mediaKey) return@launch
                val selected = mutable.value.selected ?: return@launch
                if (selected.mediaKey != item.mediaKey && selected.type != "series") return@launch
                if (
                    info.preparationStatus == "ready" &&
                        info.playbackUrl.isNotBlank() &&
                        info.playbackMode != "hls"
                )
                    playbackCoordinator.preload(repository.serverBaseUrl(), info)
            }
    }

    fun cancelPreload() {
        mediaPreloadJob?.cancel()
        mediaPreloadJob = null
        preloadMediaKey = ""
        playbackCoordinator.cancelPreload()
    }

    fun cancelPlaybackPreparation(cancelRequest: Boolean = true) {
        if (cancelRequest) {
            playbackRequestJob?.cancel()
            playbackRequestJob = null
        }
        playbackGeneration++
        playbackPreparationJob?.cancel()
        playbackPreparationJob = null
    }

    fun saveProgress(mediaKey: String, progress: WatchProgress) {
        mutable.value = mutable.value.withPlaybackProgress(mediaKey, progress)
        if (mutable.value.previewMode) return
        val profile = mutable.value.profile ?: return
        repository.enqueueProgress(profile.id, mediaKey, progress)
    }

    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) =
        launch(Session) {
            val profile = mutable.value.profile ?: return@launch
            val valid =
                if (mutable.value.previewMode) pin == "1234"
                else repository.verifyPin(profile.id, pin)
            operations.message(Session, if (valid) "" else "PIN incorreto. Tente novamente.")
            onResult(valid)
        }

    fun forget(onDone: () -> Unit) =
        launch(Session) {
            libraryScanJob?.cancel()
            homeJob?.cancel()
            catalogJob?.cancel()
            searchJob?.cancel()
            cancelPlaybackPreparation()
            cancelPreload()
            playbackCoordinator.clear()
            repository.forget()
            operations.reset()
            mutable.value = BrasaUiState()
            onDone()
        }

    override fun onCleared() {
        pairingJob?.cancel()
        searchJob?.cancel()
        metadataPrefetchJob?.cancel()
        playbackRequestJob?.cancel()
        playbackPreparationJob?.cancel()
        cancelPreload()
        super.onCleared()
    }

    private fun launch(domain: OperationDomain, block: suspend () -> Unit) =
        viewModelScope.launch { operations.run(domain, { error(it, domain) }, block) }

    fun dismissConnectionProblem() {
        mutable.value = mutable.value.copy(connectionProblem = null)
    }

    fun reportConnectionProblem(problem: com.brasa.tv.core.network.ConnectionProblem) {
        mutable.value =
            mutable.value.copy(
                connectionProblem = problem,
                paired =
                    if (problem == com.brasa.tv.core.network.ConnectionProblem.REVOKED) false
                    else mutable.value.paired,
            )
    }

    private fun error(value: Throwable, domain: OperationDomain) {
        if (value is CancellationException) throw value
        if (
            value !is java.io.IOException ||
                (value is com.brasa.tv.core.network.BrasaApiException &&
                    value.status in setOf(400, 409, 422))
        ) {
            operations.message(domain, value.message ?: "Não foi possível concluir a operação.")
            return
        }
        val problem = repository.problem(value)
        operations.message(domain, problem.explanation)
        mutable.value =
            mutable.value.copy(
                connectionProblem = problem,
                paired =
                    if (problem == com.brasa.tv.core.network.ConnectionProblem.REVOKED) false
                    else mutable.value.paired,
            )
    }

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

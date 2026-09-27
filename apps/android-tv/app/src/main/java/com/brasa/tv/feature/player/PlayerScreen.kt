@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.tv.material3.Text
import com.brasa.tv.BuildConfig
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.model.WatchProgress
import com.brasa.tv.core.playback.PlaybackDiagnosticsAttachment
import com.brasa.tv.core.playback.PlaybackDiagnosticsRecorder
import com.brasa.tv.core.playback.PlaybackEvent
import com.brasa.tv.core.playback.PlaybackRecovery
import com.brasa.tv.core.playback.PlaybackTimeline
import com.brasa.tv.core.playback.SeekPolicy
import com.brasa.tv.core.playback.SeekThumbnailLoader
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaRed
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    state: BrasaUiState,
    container: AppContainer,
    onProgress: (String, WatchProgress) -> Unit,
    onPlaybackFallback: (String) -> Unit,
    onRemoteSeek: (String, Long, Boolean) -> Unit,
    onConnectionProblem: (com.brasa.tv.core.network.ConnectionProblem) -> Unit,
    onRetry: () -> Unit,
    onNext: (CatalogItem) -> Unit,
    onSignal: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    if (state.previewMode) {
        PreviewPlayerScreen(state.playbackItem ?: state.selected, onBack)
        return
    }
    val info = state.playback
    val recovery = remember(info?.mediaKey) { PlaybackRecovery() }
    val settings by container.settings.values.collectAsState(initial = AppSettings())
    val diagnostics =
        remember(info?.mediaKey, state.profile?.id, settings.serverBaseUrl) {
            val mediaKey = info?.mediaKey
            val profileId = state.profile?.id
            if (mediaKey == null || profileId == null || settings.serverBaseUrl.isBlank()) null
            else
                PlaybackDiagnosticsRecorder(mediaKey) { batch ->
                    container.api.sendPlaybackEvents(settings.serverBaseUrl, profileId, batch)
                }
        }
    DisposableEffect(diagnostics) { onDispose { diagnostics?.finish() } }
    LaunchedEffect(info, diagnostics) { if (info != null) diagnostics?.source(info) }
    if (info == null || settings.serverBaseUrl.isBlank()) {
        BackHandler(onBack = onBack)
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Não foi possível carregar os dados de reprodução.",
                    color = BrasaRed,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrasaButton("Tentar novamente", onRetry, style = BrasaButtonStyle.Primary)
                    BrasaButton("Voltar", onBack)
                }
            }
        }
    } else if (info.preparationStatus != "ready" || info.playbackUrl.isBlank()) {
        PreparationScreen(info, onRetry, onBack)
    } else {
        val identity =
            "${info.mediaKey}|${info.playbackMode}|${info.playbackRevision}|${info.playbackUrl}"
        key(identity) {
            val selected = state.playbackItem
            val related =
                state.catalog
                    ?.let { catalog ->
                        (catalog.movies + catalog.series)
                            .filter {
                                it.mediaKey != selected?.mediaKey &&
                                    it.genres.any(selected?.genres.orEmpty()::contains)
                            }
                            .take(2)
                    }
                    .orEmpty()
            val seriesTitle =
                state.catalog
                    ?.series
                    ?.firstOrNull { series ->
                        series.seasons.any { season ->
                            season.episodes.any { episode -> episode.mediaKey == info.mediaKey }
                        }
                    }
                    ?.title
                    .orEmpty()
            PlayerContent(
                info,
                identity,
                selected,
                seriesTitle,
                related,
                settings.serverBaseUrl,
                settings,
                container,
                recovery,
                diagnostics,
                onProgress,
                onPlaybackFallback,
                onRemoteSeek,
                onConnectionProblem,
                onNext,
                onSignal,
                onBack,
            )
        }
    }
}

@Composable
private fun PlayerContent(
    info: PlaybackInfo,
    playbackIdentity: String,
    selected: CatalogItem?,
    seriesTitle: String,
    related: List<CatalogItem>,
    serverBaseUrl: String,
    settings: AppSettings,
    container: AppContainer,
    recovery: PlaybackRecovery,
    diagnostics: PlaybackDiagnosticsRecorder?,
    onProgress: (String, WatchProgress) -> Unit,
    onPlaybackFallback: (String) -> Unit,
    onRemoteSeek: (String, Long, Boolean) -> Unit,
    onConnectionProblem: (com.brasa.tv.core.network.ConnectionProblem) -> Unit,
    onNext: (CatalogItem) -> Unit,
    onSignal: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var appForeground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }
    val sessionState =
        rememberPlayerSession(playbackIdentity, serverBaseUrl, info, container.playback)
    var loadError by sessionState.error
    var loadAttempt by sessionState.attempt
    val player = sessionState.player.value
    val recoveryState = remember(playbackIdentity, serverBaseUrl) { PlayerRecoveryState() }
    var pendingRetry by recoveryState.pendingRetry
    if (player == null) {
        BackHandler(onBack = onBack)
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    loadError.ifBlank { "Preparando vídeo…" },
                    color = if (loadError.isBlank()) Color.White else BrasaRed,
                    fontSize = 20.sp,
                )
                if (loadError.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrasaButton(
                            "Tentar novamente",
                            { loadAttempt++ },
                            style = BrasaButtonStyle.Primary,
                        )
                        BrasaButton("Voltar", onBack)
                    }
                }
            }
        }
        return
    }
    val controls = remember(player) { PlayerControlsState(player, info) }
    val playFocus = controls.playFocus
    val errorFocus = controls.errorFocus
    LaunchedEffect(loadError) {
        if (loadError.isNotBlank()) {
            withFrameNanos {}
            runCatching { errorFocus.requestFocus() }
        }
    }
    var ended by controls.ended
    var autoNextSeconds by controls.autoNextSeconds
    var autoNextCancelled by controls.autoNextCancelled
    val endFocus = controls.endFocus
    LaunchedEffect(
        ended,
        settings.autoplayNext,
        autoNextCancelled,
        info.nextEpisode?.mediaKey,
        appForeground,
    ) {
        if (
            !appForeground ||
                !ended ||
                !settings.autoplayNext ||
                autoNextCancelled ||
                info.nextEpisode == null
        )
            return@LaunchedEffect
        runCatching { endFocus.requestFocus() }
        while (autoNextSeconds > 0) {
            delay(1_000)
            autoNextSeconds--
        }
        if (!autoNextCancelled) onNext(info.nextEpisode)
    }
    var controlsVisible by controls.controlsVisible
    var interaction by controls.interaction
    var position by controls.position
    var duration by controls.duration
    var centerNotice by controls.centerNotice
    var actualHeight by controls.actualHeight
    val seek = remember(player) { PlayerSeekController() }
    var seekPreview by seek.preview
    var remoteSeekTarget by seek.remoteTarget
    var recoveryRequested by seek.requested
    var trackDialogType by controls.trackDialogType
    var technicalInfoVisible by controls.technicalInfoVisible
    val infoFocus = controls.infoFocus
    var restoreInfoFocus by controls.restoreInfoFocus
    LaunchedEffect(technicalInfoVisible, restoreInfoFocus) {
        if (!technicalInfoVisible && restoreInfoFocus) {
            withFrameNanos {}
            infoFocus.requestFocus()
            restoreInfoFocus = false
        }
    }
    var restoreTrackFocus by controls.restoreTrackFocus
    val playbackScope = rememberCoroutineScope()
    val progressStatus by container.progressSync.state.collectAsState()
    val thumbnailLoader = remember(container.http) { SeekThumbnailLoader(container.http) }
    val seekThumbnail =
        rememberSeekThumbnail(thumbnailLoader, serverBaseUrl, info, seekPreview, appForeground) {
            !player.playWhenReady ||
                (player.totalBufferedDuration >= 30_000 &&
                    !player.isLoading &&
                    player.playerError == null)
        }
    PlayerNextPreparation(player, info, settings, appForeground, container, serverBaseUrl, seek)
    LaunchedEffect(player) {
        player.trackSelectionParameters =
            applyPlaybackPreferences(player.trackSelectionParameters, settings)
    }
    LaunchedEffect(trackDialogType, restoreTrackFocus) {
        if (trackDialogType == null && restoreTrackFocus) {
            withFrameNanos {}
            runCatching { playFocus.requestFocus() }
            restoreTrackFocus = false
        }
    }

    val retryPlayback =
        rememberPlaybackRetry(
            player,
            recoveryState,
            recovery,
            diagnostics,
            appForeground,
            sessionState.error,
        )

    fun saveAt(absolutePosition: Long, completed: Boolean = false) {
        val localDuration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        val total = PlaybackTimeline.absoluteDuration(info, localDuration)
        if (total <= 0) return
        val current = absolutePosition.coerceIn(0L, total)
        onProgress(
            info.mediaKey,
            WatchProgress(
                mediaType = if (info.mediaKey.startsWith("episode:")) "episode" else "movie",
                mediaId = info.mediaId,
                currentTime = current / 1000.0,
                duration = total / 1000.0,
                percentage = (current.toDouble() / total * 100).coerceIn(0.0, 100.0),
                completed = completed,
            ),
        )
    }
    fun save(completed: Boolean = false) {
        val current =
            remoteSeekTarget.takeIf { it >= 0 }
                ?: PlaybackTimeline.absolutePosition(info, player.currentPosition)
        saveAt(current, completed)
    }
    fun exit() {
        save()
        player.pause()
        onBack()
    }
    fun revealControls() {
        controlsVisible = true
        interaction++
    }
    fun requestRemoteSeek(
        targetPosition: Long,
        recovery: Boolean = false,
        adaptive: Boolean = false,
        forceConversion: Boolean = recovery || adaptive,
    ) {
        if (recoveryRequested) return
        if (recovery || adaptive) diagnostics?.conversion(if (adaptive) "network" else "recovery")
        else
            diagnostics?.record(
                PlaybackEvent(
                    kind = "seek",
                    positionMs = targetPosition.coerceAtLeast(0),
                    reason = "seek",
                )
            )
        pendingRetry?.cancel()
        pendingRetry = null
        val total = duration.takeIf { it > 0 } ?: info.duration ?: Long.MAX_VALUE
        val target = seek.beginRemoteSeek(targetPosition, total) ?: return
        saveAt(target)
        player.pause()
        centerNotice =
            if (recovery) "Reconectando em ${formatTime(target)}…"
            else "Carregando ${formatTime(target)}…"
        onRemoteSeek(info.mediaKey, target, forceConversion)
    }
    fun seekToPosition(targetPosition: Long) {
        val total = duration.takeIf { it > 0 } ?: info.duration ?: Long.MAX_VALUE
        val target = targetPosition.coerceIn(0L, (total - 1_000).coerceAtLeast(0))
        val localTarget = target - info.playbackOffset
        val canSeekLocally =
            SeekPolicy.canSeekLocally(
                info.playbackMode,
                info.supportsRange,
                player.isCurrentMediaItemSeekable,
                target,
                info.playbackOffset,
                PlaybackTimeline.absolutePosition(info, player.currentPosition),
                PlaybackTimeline.absolutePosition(info, player.bufferedPosition),
            )
        if (canSeekLocally) {
            player.seekTo(localTarget)
            position = target
            centerNotice = formatTime(target)
        } else {
            requestRemoteSeek(target)
        }
        revealControls()
    }
    fun seekBy(delta: Long) {
        seekToPosition(PlaybackTimeline.absolutePosition(info, player.currentPosition) + delta)
    }

    BackHandler {
        if (technicalInfoVisible) {
            technicalInfoVisible = false
            restoreInfoFocus = true
        } else exit()
    }
    PlayerLifecycleEffect(
        player,
        { save() },
        {
            pendingRetry?.cancel()
            pendingRetry = null
            recovery.resetSampling()
        },
        {
            appForeground = it
            if (!it) controlsVisible = true
        },
    )
    PlaybackRecoveryEffects(
        player,
        info,
        recoveryState,
        recovery,
        sessionState.error,
        appForeground,
        { recoveryRequested },
        { ended },
        ::revealControls,
        retryPlayback,
        { requestRemoteSeek(it, recovery = true) },
    )
    val diagnosticsAttachment =
        remember(player, diagnostics) {
            diagnostics?.let { recorder ->
                PlaybackDiagnosticsAttachment(
                    player,
                    info,
                    recorder,
                    onAdaptiveFallback = {
                        requestRemoteSeek(
                            PlaybackTimeline.absolutePosition(info, player.currentPosition),
                            recovery = true,
                            adaptive = true,
                        )
                    },
                    onQuality = { actualHeight = it },
                )
            }
        }
    DisposableEffect(diagnosticsAttachment) { onDispose { diagnosticsAttachment?.detach() } }
    LaunchedEffect(diagnosticsAttachment) {
        while (true) {
            delay(15_000)
            diagnosticsAttachment?.sample()
        }
    }
    val listener =
        rememberPlayerListener(
            player,
            info,
            container,
            controls,
            recoveryState,
            recovery,
            sessionState.error,
            appForeground,
            playbackScope,
            ::save,
            { target, convert ->
                requestRemoteSeek(target, recovery = true, forceConversion = convert)
            },
            ::revealControls,
            retryPlayback,
            onConnectionProblem,
        )
    PlayerSessionEffect(player, container.playback, listener) {
        pendingRetry?.cancel()
        pendingRetry = null
        save()
        ended
    }
    LaunchedEffect(player) {
        while (true) {
            delay(12_000)
            if (player.isPlaying) save()
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Buffer ${info.mediaKey}: ${player.totalBufferedDuration}ms")
        }
    }
    PlayerControlsEffects(player, info, controls, seek, appForeground, ::revealControls)

    PlayerOverlay(
        info,
        selected,
        seriesTitle,
        related,
        serverBaseUrl,
        settings,
        container,
        player,
        controls,
        seek,
        seekThumbnail,
        progressStatus,
        sessionState.error,
        PlayerOverlayActions(
            ::seekBy,
            ::seekToPosition,
            ::saveAt,
            ::exit,
            retryPlayback,
            ::revealControls,
            onNext,
            onSignal,
        ),
    )
}

private const val TAG = "BRasaPlayback"

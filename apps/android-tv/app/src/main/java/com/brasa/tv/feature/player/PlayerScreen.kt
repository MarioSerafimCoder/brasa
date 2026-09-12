@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.BuildConfig
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.model.WatchProgress
import com.brasa.tv.core.model.playableItem
import com.brasa.tv.core.playback.PlaybackTimeline
import com.brasa.tv.core.playback.SeekPolicy
import com.brasa.tv.core.playback.PlaybackRecovery
import com.brasa.tv.core.playback.PlaybackDiagnosticsRecorder
import com.brasa.tv.core.playback.PlaybackDiagnosticsAttachment
import com.brasa.tv.core.playback.PlaybackEvent
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaSurface
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.Locale

@Composable
fun PlayerScreen(
    state: BrasaUiState,
    container: AppContainer,
    onProgress: (String, WatchProgress) -> Unit,
    onPlaybackFallback: (String) -> Unit,
    onRemoteSeek: (String, Long) -> Unit,
    onRetry: () -> Unit,
    onNext: (CatalogItem) -> Unit,
    onSignal: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    if (state.previewMode) {
        PreviewPlayerScreen(state.selected, onBack)
        return
    }
    val info = state.playback
    val recovery = remember(info?.mediaKey) { PlaybackRecovery() }
    val settings by container.settings.values.collectAsState(initial = AppSettings())
    val diagnostics = remember(info?.mediaKey, state.profile?.id, settings.serverBaseUrl) {
        val mediaKey = info?.mediaKey
        val profileId = state.profile?.id
        if (mediaKey == null || profileId == null || settings.serverBaseUrl.isBlank()) null
        else PlaybackDiagnosticsRecorder(mediaKey) { batch -> container.api.sendPlaybackEvents(settings.serverBaseUrl, profileId, batch) }
    }
    DisposableEffect(diagnostics) { onDispose { diagnostics?.finish() } }
    LaunchedEffect(info, diagnostics) { if (info != null) diagnostics?.source(info) }
    if (info == null || settings.serverBaseUrl.isBlank()) {
        BackHandler(onBack = onBack)
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Não foi possível carregar os dados de reprodução.", color = BrasaRed, fontSize = 20.sp)
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
        val identity = "${info.mediaKey}|${info.playbackMode}|${info.playbackRevision}|${info.playbackUrl}"
        key(identity) {
            val selected = state.selected
            val related = state.catalog?.let { catalog -> (catalog.movies + catalog.series).filter { it.mediaKey != selected?.mediaKey && it.genres.any(selected?.genres.orEmpty()::contains) }.take(2) }.orEmpty()
            PlayerContent(info, identity, selected, related, settings.serverBaseUrl, settings, container, recovery, diagnostics, onProgress, onPlaybackFallback, onRemoteSeek, onNext, onSignal, onBack)
        }
    }
}

@Composable
private fun PreparationScreen(info: PlaybackInfo, onRetry: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val failed = info.preparationStatus == "failed"
    Box(Modifier.fillMaxSize().background(Color.Black).focusable(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (failed) "Não foi possível preparar o vídeo" else if (info.preparationStatus == "analyzing") "Analisando mídia" else "Preparando reprodução", color = if (failed) BrasaRed else Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (!failed) {
                Text("${info.preparationProgress.toInt()}%", color = BrasaOrange, fontSize = 24.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (info.playbackMode == "hls") "Criando streaming adaptativo. A reprodução começa com os primeiros segmentos." else "Criando uma versão compatível com esta TV.", color = BrasaTextMuted, fontSize = 17.sp)
            } else Text(info.errorMessage.ifBlank { when (info.errorType) { "network" -> "Não foi possível receber os dados do servidor."; "decode" -> "O dispositivo não conseguiu decodificar este vídeo."; "codec" -> "O formato original não é compatível com este dispositivo."; else -> "O servidor não conseguiu processar esta mídia." } }, color = BrasaTextMuted, fontSize = 17.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (failed) BrasaButton("Tentar novamente", onRetry, style = BrasaButtonStyle.Primary)
                BrasaButton("Voltar", onBack)
            }
        }
    }
}

@Composable
private fun PlayerContent(
    info: PlaybackInfo,
    playbackIdentity: String,
    selected: CatalogItem?,
    related: List<CatalogItem>,
    serverBaseUrl: String,
    settings: AppSettings,
    container: AppContainer,
    recovery: PlaybackRecovery,
    diagnostics: PlaybackDiagnosticsRecorder?,
    onProgress: (String, WatchProgress) -> Unit,
    onPlaybackFallback: (String) -> Unit,
    onRemoteSeek: (String, Long) -> Unit,
    onNext: (CatalogItem) -> Unit,
    onSignal: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }
    var acquiredPlayer by remember(playbackIdentity, serverBaseUrl) { mutableStateOf<ExoPlayer?>(null) }
    var loadError by remember(playbackIdentity, serverBaseUrl) { mutableStateOf("") }
    var loadAttempt by remember(playbackIdentity, serverBaseUrl) { mutableIntStateOf(0) }
    var retryPositionOverride by remember(playbackIdentity, serverBaseUrl) { mutableLongStateOf(info.resumePosition) }
    var retryCount by remember(playbackIdentity, serverBaseUrl) { mutableIntStateOf(0) }
    var pendingRetry by remember(playbackIdentity, serverBaseUrl) { mutableStateOf<Job?>(null) }
    var fallbackRequested by remember(playbackIdentity, serverBaseUrl) { mutableStateOf(false) }
    LaunchedEffect(playbackIdentity, serverBaseUrl, loadAttempt) {
        acquiredPlayer = null
        loadError = ""
        runCatching { container.playback.acquire(serverBaseUrl, info.copy(resumePosition = retryPositionOverride)) }
            .onSuccess { acquiredPlayer = it }
            .onFailure { loadError = it.message ?: "Não foi possível preparar o vídeo." }
    }
    val player = acquiredPlayer
    if (player == null) {
        BackHandler(onBack = onBack)
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadError.ifBlank { "Preparando vídeo…" }, color = if (loadError.isBlank()) Color.White else BrasaRed, fontSize = 20.sp)
                if (loadError.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrasaButton("Tentar novamente", { loadAttempt++ }, style = BrasaButtonStyle.Primary)
                        BrasaButton("Voltar", onBack)
                    }
                }
            }
        }
        return
    }
    val session = remember(player) { MediaSession.Builder(context, player).build() }
    var firstFrameRendered by remember(player) { mutableStateOf(false) }
    var retrySequence by remember(player) { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    LaunchedEffect(loadError) {
        if (loadError.isNotBlank()) {
            withFrameNanos { }
            runCatching { errorFocus.requestFocus() }
        }
    }
    var ended by remember { mutableStateOf(false) }
    var autoNextSeconds by remember(info.mediaKey) { mutableIntStateOf(10) }
    var autoNextCancelled by remember(info.mediaKey) { mutableStateOf(false) }
    val endFocus = remember { FocusRequester() }
    LaunchedEffect(ended, settings.autoplayNext, autoNextCancelled, info.nextEpisode?.mediaKey) {
        if (!ended || !settings.autoplayNext || autoNextCancelled || info.nextEpisode == null) return@LaunchedEffect
        runCatching { endFocus.requestFocus() }
        while (autoNextSeconds > 0) { delay(1_000); autoNextSeconds-- }
        if (!autoNextCancelled) onNext(info.nextEpisode)
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var playRequested by remember(player) { mutableStateOf(player.playWhenReady) }
    var position by remember { mutableLongStateOf(PlaybackTimeline.absolutePosition(info, player.currentPosition)) }
    var duration by remember { mutableLongStateOf(PlaybackTimeline.absoluteDuration(info, 0)) }
    var buffered by remember { mutableLongStateOf(PlaybackTimeline.absolutePosition(info, player.bufferedPosition)) }
    var trackNotice by remember { mutableStateOf("") }
    var centerNotice by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf("Automática") }
    var actualHeight by remember(player) { mutableIntStateOf(player.videoFormat?.height ?: 0) }
    var timelineFocused by remember(player) { mutableStateOf(false) }
    var seekPreview by remember(player) { mutableLongStateOf(-1L) }
    var remoteSeekTarget by remember(player) { mutableLongStateOf(-1L) }
    var recoveryRequested by remember(player) { mutableStateOf(false) }
    var trackDialogType by remember(player) { mutableStateOf<Int?>(null) }
    var restoreTrackFocus by remember(player) { mutableStateOf(false) }
    var currentTracks by remember(player) { mutableStateOf(player.currentTracks) }
    val playbackScope = rememberCoroutineScope()
    LaunchedEffect(player) {
        player.trackSelectionParameters = applyPlaybackPreferences(player.trackSelectionParameters, settings)
    }
    LaunchedEffect(trackDialogType, restoreTrackFocus) {
        if (trackDialogType == null && restoreTrackFocus) {
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
            restoreTrackFocus = false
        }
    }

    fun retryPlayback() {
        diagnostics?.record(PlaybackEvent(kind = "retry"))
        loadError = ""
        val resumePlayback = player.playWhenReady
        // This is local player time, including when resuming an offset HLS playlist.
        retryPositionOverride = player.currentPosition.coerceAtLeast(0)
        recovery.resetSampling()
        // Reprepare the owned player. Reacquiring the same identity can return the
        // old instance just as Compose disposes it, leaving a released player on screen.
        pendingRetry = null
        firstFrameRendered = false
        retrySequence++
        player.stop()
        player.seekTo(retryPositionOverride)
        player.prepare()
        player.playWhenReady = resumePlayback
    }

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
        val current = remoteSeekTarget.takeIf { it >= 0 }
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
    fun requestRemoteSeek(targetPosition: Long, recovery: Boolean = false, adaptive: Boolean = false) {
        if (recoveryRequested) return
        if (recovery || adaptive) diagnostics?.conversion(if (adaptive) "network" else "recovery")
        else diagnostics?.record(PlaybackEvent(kind = "seek", positionMs = targetPosition.coerceAtLeast(0), reason = "seek"))
        pendingRetry?.cancel()
        pendingRetry = null
        val total = duration.takeIf { it > 0 } ?: info.duration ?: Long.MAX_VALUE
        val target = targetPosition.coerceIn(0L, (total - 1_000).coerceAtLeast(0))
        recoveryRequested = true
        remoteSeekTarget = target
        saveAt(target)
        player.pause()
        centerNotice = if (recovery) "Reconectando em ${formatTime(target)}…" else "Carregando ${formatTime(target)}…"
        onRemoteSeek(info.mediaKey, target)
    }
    fun seekToPosition(targetPosition: Long) {
        val total = duration.takeIf { it > 0 } ?: info.duration ?: Long.MAX_VALUE
        val target = targetPosition.coerceIn(0L, (total - 1_000).coerceAtLeast(0))
        val localTarget = target - info.playbackOffset
        val canSeekLocally = SeekPolicy.canSeekLocally(
            info.playbackMode, info.supportsRange, player.isCurrentMediaItemSeekable,
            target, info.playbackOffset, PlaybackTimeline.absolutePosition(info, player.currentPosition),
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

    BackHandler { exit() }
    val diagnosticsAttachment = remember(player, diagnostics) {
        diagnostics?.let { recorder -> PlaybackDiagnosticsAttachment(player, info, recorder,
            onAdaptiveFallback = { requestRemoteSeek(PlaybackTimeline.absolutePosition(info, player.currentPosition), recovery = true, adaptive = true) },
            onQuality = { actualHeight = it }) }
    }
    DisposableEffect(diagnosticsAttachment) { onDispose { diagnosticsAttachment?.detach() } }
    LaunchedEffect(diagnosticsAttachment) { while (true) { delay(15_000); diagnosticsAttachment?.sample() } }
    DisposableEffect(player) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val startedAt = container.playback.startedAt(player)
        var ready = false
        var rebufferStartedAt = 0L
        var rebufferCount = 0
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { currentTracks = tracks }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playRequested = playWhenReady }
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value; revealControls() }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val now = SystemClock.elapsedRealtime()
                if (playbackState == Player.STATE_BUFFERING && ready && rebufferStartedAt == 0L) rebufferStartedAt = now
                if (playbackState == Player.STATE_READY) {
                    if (!ready) Log.i(TAG, "STATE_READY ${info.mediaKey} em ${now - startedAt}ms")
                    ready = true
                    if (rebufferStartedAt > 0L) {
                        rebufferCount++
                        Log.i(TAG, "Rebuffer #$rebufferCount ${info.mediaKey}: ${now - rebufferStartedAt}ms")
                        rebufferStartedAt = 0L
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    val absolute = PlaybackTimeline.absolutePosition(info, player.currentPosition)
                    val total = PlaybackTimeline.absoluteDuration(info, player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L)
                    if (PlaybackRecovery.isPrematureEnd(absolute, total)) {
                        save()
                        Log.w(TAG, "Mídia terminou antes da duração esperada em ${info.mediaKey}: $absolute/$total, modo=${info.playbackMode}")
                        if (recovery.canRecoverPrematureEnd(absolute)) requestRemoteSeek(absolute, recovery = true)
                        else {
                            loadError = "O vídeo termina antes do esperado neste trecho. O arquivo pode estar incompleto ou danificado; confira a cópia no computador."
                            player.pause()
                            controlsVisible = true
                        }
                    } else {
                        save(true)
                        ended = true
                        controlsVisible = true
                    }
                }
            }
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
                Log.i(TAG, "Primeiro frame ${info.mediaKey} em ${SystemClock.elapsedRealtime() - startedAt}ms")
            }
            override fun onPlayerError(error: PlaybackException) {
                val recoverable = error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                if (info.playbackMode == "direct" && isCompatibilityError(error) && !fallbackRequested) {
                    fallbackRequested = true
                    loadError = ""
                    requestRemoteSeek(PlaybackTimeline.absolutePosition(info, player.currentPosition), recovery = true)
                    Log.w(TAG, "Fallback HLS solicitado para ${info.mediaKey}: ${error.errorCodeName}", error)
                } else if (recoverable && pendingRetry?.isActive == true) {
                    return
                } else if (recoverable && recovery.beginRetry()) {
                    retryCount = recovery.attempts
                    loadError = ""
                    pendingRetry = playbackScope.launch { delay(1_500L * retryCount); retryPlayback() }
                    Log.w(TAG, "Tentativa $retryCount para ${info.mediaKey}: ${error.errorCodeName}", error)
                } else {
                    loadError = publicPlaybackError(error)
                    Log.e(TAG, "Falha ${info.mediaKey}: ${error.errorCodeName}", error)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            pendingRetry?.cancel()
            pendingRetry = null
            save()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            player.removeListener(listener)
            session.release()
            container.playback.release(player, completed = ended)
        }
    }
    LaunchedEffect(player, info.playbackMode, retrySequence) {
        val startup = PlaybackRecovery()
        while (!firstFrameRendered && !recoveryRequested && loadError.isBlank()) {
            delay(2_000)
            val waitingForFrame = player.playWhenReady && player.playerError == null &&
                player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                pendingRetry?.isActive != true && player.playbackState in setOf(Player.STATE_BUFFERING, Player.STATE_READY)
            if (!startup.sample(SystemClock.elapsedRealtime(), 0, waitingForFrame,
                    buffering = player.playbackState == Player.STATE_BUFFERING,
                    bufferedPositionMs = player.bufferedPosition)) continue
            if (info.playbackMode == "direct" && !fallbackRequested) {
                fallbackRequested = true
                loadError = ""
                requestRemoteSeek(PlaybackTimeline.absolutePosition(info, player.currentPosition), recovery = true)
                Log.w(TAG, "Fallback HLS solicitado: buffer recebido sem primeiro quadro para ${info.mediaKey}")
            } else if (recovery.beginRetry()) {
                retryCount = recovery.attempts
                retryPlayback()
                Log.w(TAG, "Player recriado: buffer recebido sem primeiro quadro para ${info.mediaKey}")
            } else {
                loadError = "A TV recebeu o vídeo, mas não conseguiu exibir o primeiro quadro. Tente novamente."
                player.pause()
                Log.e(TAG, "Retomada sem primeiro quadro após $retryCount tentativas para ${info.mediaKey}")
            }
            break
        }
    }
    LaunchedEffect(player, firstFrameRendered, retrySequence) {
        if (!firstFrameRendered) return@LaunchedEffect
        recovery.resetSampling()
        while (!recoveryRequested && !ended) {
            delay(2_000)
            val currentPosition = player.currentPosition
            val shouldAdvance = player.playWhenReady && player.playbackState != Player.STATE_ENDED &&
                player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                player.playerError == null && pendingRetry?.isActive != true && loadError.isBlank()
            if (recovery.sample(SystemClock.elapsedRealtime(), currentPosition, shouldAdvance,
                    buffering = player.playbackState == Player.STATE_BUFFERING,
                    bufferedPositionMs = player.bufferedPosition)) {
                val absolute = PlaybackTimeline.absolutePosition(info, currentPosition)
                Log.w(TAG, "Reprodução sem avanço em ${info.mediaKey}: position=$absolute, state=${player.playbackState}, isPlaying=${player.isPlaying}")
                if (recovery.beginRetry()) {
                    retryCount = recovery.attempts
                    retryPlayback()
                } else if (recovery.canRecoverPrematureEnd(absolute)) {
                    requestRemoteSeek(absolute, recovery = true)
                } else {
                    loadError = "Não foi possível continuar neste trecho. Confira a conexão e se o arquivo do vídeo está completo no computador."
                    player.pause()
                    controlsVisible = true
                }
                break
            }
        }
    }
    LaunchedEffect(player) { while (true) { delay(12_000); if (player.isPlaying) save(); if (BuildConfig.DEBUG) Log.d(TAG, "Buffer ${info.mediaKey}: ${player.totalBufferedDuration}ms") } }
    LaunchedEffect(controlsVisible, interaction, isPlaying, trackDialogType) {
        if (controlsVisible && isPlaying && !ended && trackDialogType == null) {
            delay(4_000)
            controlsVisible = false
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && !ended) {
            delay(80)
            runCatching { playFocus.requestFocus() }
        }
    }
    LaunchedEffect(player, controlsVisible) {
        while (true) {
            val localDuration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            position = PlaybackTimeline.absolutePosition(info, player.currentPosition)
            duration = PlaybackTimeline.absoluteDuration(info, localDuration)
            buffered = PlaybackTimeline.absolutePosition(info, player.bufferedPosition).coerceAtLeast(position)
            delay(if (controlsVisible) 500 else 1_500)
        }
    }
    LaunchedEffect(trackNotice) {
        if (trackNotice.isNotBlank()) { delay(2_400); trackNotice = "" }
    }
    LaunchedEffect(centerNotice) { if (centerNotice.isNotBlank()) { delay(900); centerNotice = "" } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val wasVisible = controlsVisible
                revealControls()
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-10_000); true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(10_000); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (player.playWhenReady) { player.pause(); centerNotice = "Pausado" } else { player.play(); centerNotice = "Reproduzindo" }; true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); centerNotice = "Pausado"; true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); centerNotice = "Reproduzindo"; true }
                    else -> !wasVisible
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; this.player = player } },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.player = player
                view.subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * settings.subtitleSize)
                    setStyle(CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        if (settings.subtitleStyle == "background") 0xB3000000.toInt() else android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK, null,
                    ))
                }
            },
        )

        if (loadError.isNotBlank()) {
            Column(
                Modifier.align(Alignment.Center).width(560.dp).background(BrasaSurface.copy(alpha = .97f), RoundedCornerShape(16.dp)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Falha na reprodução", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(loadError, color = BrasaTextMuted, fontSize = 17.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrasaButton("Tentar novamente", { player.playWhenReady = true; retryPlayback() }, Modifier.focusRequester(errorFocus), style = BrasaButtonStyle.Primary)
                    BrasaButton("Voltar", ::exit)
                }
            }
        }

        if (controlsVisible && loadError.isBlank()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = .55f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .9f)),
                    ),
                ),
            )
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 34.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected?.title.orEmpty().ifBlank { "Reproduzindo agora" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 52.dp, vertical = 30.dp),
            ) {
                if (trackNotice.isNotBlank()) {
                    Text(trackNotice, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(9.dp))
                }
                val visiblePosition = seekPreview.takeIf { it >= 0 } ?: position
                val timelineModifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        timelineFocused = it.isFocused
                        if (!it.isFocused) seekPreview = -1L
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                        val step = when {
                            event.nativeKeyEvent.repeatCount >= 8 -> 120_000L
                            event.nativeKeyEvent.repeatCount >= 3 -> 30_000L
                            else -> 10_000L
                        }
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                val current = seekPreview.takeIf { it >= 0 } ?: position
                                seekPreview = (current - step).coerceAtLeast(0)
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                val current = seekPreview.takeIf { it >= 0 } ?: position
                                seekPreview = (current + step).coerceAtMost((duration - 1_000).coerceAtLeast(0))
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                seekToPosition(seekPreview.takeIf { it >= 0 } ?: position)
                                seekPreview = -1L
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .then(if (timelineFocused) Modifier.border(2.dp, BrasaOrange, RoundedCornerShape(10.dp)).padding(8.dp) else Modifier)
                Row(timelineModifier, verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(visiblePosition), color = if (timelineFocused) BrasaOrange else BrasaText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(13.dp))
                    Box(Modifier.weight(1f).height(5.dp).background(Color.White.copy(alpha = .24f), RoundedCornerShape(50))) {
                        if (duration > 0) Box(
                            Modifier.fillMaxWidth((buffered.toFloat() / duration).coerceIn(0f, 1f)).fillMaxHeight().background(Color.White.copy(alpha = .45f), RoundedCornerShape(50)),
                        )
                        if (duration > 0) Box(
                            Modifier.fillMaxWidth((visiblePosition.toFloat() / duration).coerceIn(0f, 1f)).fillMaxHeight().background(BrasaOrange, RoundedCornerShape(50)),
                        )
                    }
                    Spacer(Modifier.width(13.dp))
                    Text(formatTime(duration), color = BrasaTextMuted, fontSize = 14.sp)
                }
                Text(
                    if (timelineFocused) "← → escolha o ponto  •  OK para carregar"
                    else "Qualidade: $selectedQuality${if (actualHeight > 0) " · ${actualHeight}p" else ""}  •  Buffer: ${((buffered - position).coerceAtLeast(0) / 1000)}s",
                    color = if (timelineFocused) BrasaOrange else BrasaTextMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(17.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrasaButton("10s", { seekBy(-10_000) }, leading = "↶")
                    Spacer(Modifier.width(11.dp))
                    BrasaButton(
                        if (playRequested) "Pausar" else "Reproduzir",
                        { if (player.playWhenReady) { player.pause(); centerNotice = "Pausado" } else { player.play(); centerNotice = "Reproduzindo" } },
                        Modifier.focusRequester(playFocus),
                        style = BrasaButtonStyle.Primary,
                        leading = if (playRequested) "Ⅱ" else "▶",
                    )
                    Spacer(Modifier.width(11.dp))
                    BrasaButton("10s", { seekBy(10_000) }, leading = "↷")
                    Spacer(Modifier.width(22.dp))
                    BrasaButton("Áudio", { trackDialogType = C.TRACK_TYPE_AUDIO; revealControls() })
                    Spacer(Modifier.width(9.dp))
                    BrasaButton("Legenda", { trackDialogType = C.TRACK_TYPE_TEXT; revealControls() })
                    if (info.playbackMode == "hls") {
                        Spacer(Modifier.width(9.dp))
                        BrasaButton(selectedQuality, { selectedQuality = cycleQuality(player, info, selectedQuality); trackNotice = "Qualidade: $selectedQuality"; revealControls() })
                    }
                }
            }
        }

        if (centerNotice.isNotBlank()) {
            Text(centerNotice, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .72f), RoundedCornerShape(50)).padding(horizontal = 26.dp, vertical = 14.dp), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        trackDialogType?.let { type ->
            TrackSelectionDialog(
                type = type, tracks = playbackTracks(currentTracks, type),
                subtitlesDisabled = C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes,
                settings = settings,
                onSelect = { track ->
                    player.trackSelectionParameters = selectPlaybackTrack(player.trackSelectionParameters, type, track)
                    playbackScope.launch {
                        if (type == C.TRACK_TYPE_AUDIO) container.settings.saveAudioLanguage(settings.selectedProfileId, track?.language.orEmpty())
                        else container.settings.saveSubtitleChoice(settings.selectedProfileId, if (track == null) "off" else "language", track?.language.orEmpty())
                    }
                    trackDialogType = null
                    revealControls()
                    restoreTrackFocus = true
                },
                onAutomaticAudio = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setPreferredAudioLanguage(null)
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build()
                    playbackScope.launch { container.settings.saveAudioLanguage(settings.selectedProfileId, "") }
                    trackDialogType = null
                    revealControls()
                    restoreTrackFocus = true
                },
                onSize = { size -> playbackScope.launch { container.settings.saveSubtitleSize(settings.selectedProfileId, size) } },
                onStyle = { style -> playbackScope.launch { container.settings.saveSubtitleStyle(settings.selectedProfileId, style) } },
                onDismiss = { trackDialogType = null; revealControls(); restoreTrackFocus = true },
            )
        }

        if (ended) {
            Column(
                Modifier.align(Alignment.Center).width(620.dp).background(BrasaSurface.copy(alpha = .97f), RoundedCornerShape(16.dp)).padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (info.nextEpisode != null) {
                    Text("Próximo episódio", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text(info.nextEpisode.title, color = BrasaTextMuted, fontSize = 18.sp)
                    if (settings.autoplayNext && !autoNextCancelled) Text("Reprodução automática em $autoNextSeconds s", color = BrasaOrange, fontSize = 16.sp)
                    Spacer(Modifier.height(21.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrasaButton("Reproduzir agora", { onNext(info.nextEpisode) }, style = BrasaButtonStyle.Primary, leading = "▶")
                        if (settings.autoplayNext && !autoNextCancelled) BrasaButton("Cancelar contagem", { autoNextCancelled = true }, Modifier.focusRequester(endFocus))
                        BrasaButton("Voltar à série", ::exit)
                    }
                } else {
                    Text("Você terminou ${selected?.title.orEmpty()}", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BrasaButton("Gostei", { onSignal("like", true) }, style = BrasaButtonStyle.Primary)
                        BrasaButton("Não é para mim", { onSignal("not-for-me", true) })
                        BrasaButton("Voltar", ::exit)
                    }
                    if (related.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp)); Text("Talvez você também goste", color=BrasaText,fontSize=18.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){related.forEach{candidate->BrasaButton(candidate.title.take(24),{onNext(candidate.playableItem())},style=BrasaButtonStyle.Ghost)}}
                    }
                }
            }
        }
    }
}

private const val TAG = "BRasaPlayback"

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}


private fun cycleQuality(player: ExoPlayer, info: PlaybackInfo, current: String): String {
    val options = listOf("Automática") + info.qualities
    val next = options[(options.indexOf(current).coerceAtLeast(0) + 1) % options.size]
    val builder = player.trackSelectionParameters.buildUpon()
    player.trackSelectionParameters = if (next == "Automática") builder.clearVideoSizeConstraints().build() else builder.setMaxVideoSize(Int.MAX_VALUE, next.filter(Char::isDigit).toIntOrNull() ?: 2160).build()
    return next
}

private fun publicPlaybackError(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Não foi possível receber os dados do servidor."
    PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "O dispositivo não conseguiu decodificar este vídeo."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "O formato original não é compatível com este dispositivo."
    else -> "Não foi possível reproduzir esta mídia."
}

private fun isCompatibilityError(error: PlaybackException): Boolean = error.errorCode in setOf(
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
)

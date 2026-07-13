@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
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
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaSurface
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PlayerScreen(
    state: BrasaUiState,
    container: AppContainer,
    onProgress: (WatchProgress) -> Unit,
    onNext: (CatalogItem) -> Unit,
    onBack: () -> Unit,
) {
    if (state.previewMode) {
        PreviewPlayerScreen(state.selected, onBack)
        return
    }
    val info = state.playback
    val settings by container.settings.values.collectAsState(initial = AppSettings())
    if (info == null || settings.serverBaseUrl.isBlank()) {
        BackHandler(onBack = onBack)
    } else {
        key(info.mediaKey) {
            PlayerContent(info, state.selected?.title.orEmpty(), settings.serverBaseUrl, container, onProgress, onNext, onBack)
        }
    }
}

@Composable
private fun PlayerContent(
    info: PlaybackInfo,
    title: String,
    serverBaseUrl: String,
    container: AppContainer,
    onProgress: (WatchProgress) -> Unit,
    onNext: (CatalogItem) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var acquiredPlayer by remember(info.mediaKey, serverBaseUrl) { mutableStateOf<ExoPlayer?>(null) }
    var loadError by remember(info.mediaKey, serverBaseUrl) { mutableStateOf("") }
    LaunchedEffect(info.mediaKey, serverBaseUrl) {
        runCatching { container.playback.acquire(serverBaseUrl, info) }
            .onSuccess { acquiredPlayer = it }
            .onFailure { loadError = it.message ?: "Não foi possível preparar o vídeo." }
    }
    val player = acquiredPlayer
    if (player == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(loadError.ifBlank { "Preparando vídeo…" }, color = if (loadError.isBlank()) Color.White else BrasaRed, fontSize = 20.sp)
        }
        return
    }
    val session = remember(player) { MediaSession.Builder(context, player).build() }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    var ended by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0)) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(player.bufferedPosition.coerceAtLeast(0)) }
    var trackNotice by remember { mutableStateOf("") }
    var centerNotice by remember { mutableStateOf("") }

    fun save(completed: Boolean = false) {
        val total = player.duration
        if (total <= 0 || total == C.TIME_UNSET) return
        onProgress(
            WatchProgress(
                mediaType = if (info.mediaKey.startsWith("episode:")) "episode" else "movie",
                mediaId = info.mediaId,
                currentTime = player.currentPosition / 1000.0,
                duration = total / 1000.0,
                percentage = player.currentPosition.toDouble() / total * 100,
                completed = completed,
            ),
        )
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
    fun seekBy(delta: Long) {
        val end = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + delta).coerceIn(0L, end))
        position = player.currentPosition
        centerNotice = if (delta < 0) "↶ 10s" else "10s ↷"
        revealControls()
    }

    BackHandler { exit() }
    DisposableEffect(player) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val startedAt = container.playback.startedAt(player)
        var ready = false
        var rebufferStartedAt = 0L
        var rebufferCount = 0
        val listener = object : Player.Listener {
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
                if (playbackState == Player.STATE_ENDED) { save(true); ended = true; controlsVisible = true }
            }
            override fun onRenderedFirstFrame() { Log.i(TAG, "Primeiro frame ${info.mediaKey} em ${SystemClock.elapsedRealtime() - startedAt}ms") }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                loadError = "Não foi possível reproduzir esta mídia (${error.errorCodeName})."
                Log.e(TAG, "Falha ${info.mediaKey}: ${error.errorCodeName}")
            }
        }
        player.addListener(listener)
        onDispose {
            save()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            player.removeListener(listener)
            session.release()
            container.playback.release(player, completed = ended)
        }
    }
    LaunchedEffect(player) { while (true) { delay(12_000); if (player.isPlaying) save(); if (BuildConfig.DEBUG) Log.d(TAG, "Buffer ${info.mediaKey}: ${player.totalBufferedDuration}ms") } }
    LaunchedEffect(controlsVisible, interaction, isPlaying) {
        if (controlsVisible && isPlaying && !ended) {
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
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            buffered = player.bufferedPosition.coerceAtLeast(position)
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
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (player.isPlaying) { player.pause(); centerNotice = "Pausado" } else { player.play(); centerNotice = "Reproduzindo" }; true }
                    else -> !wasVisible
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; this.player = player } },
            modifier = Modifier.fillMaxSize(),
            update = { it.player = player },
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
                BrasaButton("Voltar", ::exit, style = BrasaButtonStyle.Primary)
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
                Text(title.ifBlank { "Reproduzindo agora" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 52.dp, vertical = 30.dp),
            ) {
                if (trackNotice.isNotBlank()) {
                    Text(trackNotice, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(9.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(position), color = BrasaText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(13.dp))
                    Box(Modifier.weight(1f).height(5.dp).background(Color.White.copy(alpha = .24f), RoundedCornerShape(50))) {
                        if (duration > 0) Box(
                            Modifier.fillMaxWidth((buffered.toFloat() / duration).coerceIn(0f, 1f)).fillMaxHeight().background(Color.White.copy(alpha = .45f), RoundedCornerShape(50)),
                        )
                        if (duration > 0) Box(
                            Modifier.fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).fillMaxHeight().background(BrasaOrange, RoundedCornerShape(50)),
                        )
                    }
                    Spacer(Modifier.width(13.dp))
                    Text(formatTime(duration), color = BrasaTextMuted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(17.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrasaButton("10s", { seekBy(-10_000) }, leading = "↶")
                    Spacer(Modifier.width(11.dp))
                    BrasaButton(
                        if (isPlaying) "Pausar" else "Reproduzir",
                        { if (player.isPlaying) { player.pause(); centerNotice = "Pausado" } else { player.play(); centerNotice = "Reproduzindo" } },
                        Modifier.focusRequester(playFocus),
                        style = BrasaButtonStyle.Primary,
                        leading = if (isPlaying) "Ⅱ" else "▶",
                    )
                    Spacer(Modifier.width(11.dp))
                    BrasaButton("10s", { seekBy(10_000) }, leading = "↷")
                    Spacer(Modifier.width(22.dp))
                    BrasaButton("Áudio", { trackNotice = cycleAudio(player); revealControls() })
                    Spacer(Modifier.width(9.dp))
                    BrasaButton("Legenda", { trackNotice = cycleSubtitle(player); revealControls() })
                }
            }
        }

        if (centerNotice.isNotBlank()) {
            Text(centerNotice, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .72f), RoundedCornerShape(50)).padding(horizontal = 26.dp, vertical = 14.dp), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        if (ended && info.nextEpisode != null) {
            Column(
                Modifier.align(Alignment.Center).width(510.dp).background(BrasaSurface.copy(alpha = .97f), RoundedCornerShape(16.dp)).padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Próximo episódio", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text(info.nextEpisode.title, color = BrasaTextMuted, fontSize = 18.sp)
                Spacer(Modifier.height(21.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrasaButton("Reproduzir agora", { onNext(info.nextEpisode) }, style = BrasaButtonStyle.Primary, leading = "▶")
                    BrasaButton("Voltar à série", ::exit)
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

private fun cycleAudio(player: ExoPlayer): String {
    val languages = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { group ->
        (0 until group.length).mapNotNull { group.getTrackFormat(it).language }
    }.distinct()
    if (languages.isEmpty()) return "Nenhuma faixa de áudio alternativa"
    val current = player.trackSelectionParameters.preferredAudioLanguages.firstOrNull()
    val next = languages[(languages.indexOf(current) + 1).coerceAtLeast(0) % languages.size]
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(next).build()
    return "Áudio: ${next.uppercase(Locale.ROOT)}"
}

private fun cycleSubtitle(player: ExoPlayer): String {
    val languages = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { group ->
        (0 until group.length).mapNotNull { group.getTrackFormat(it).language }
    }.distinct()
    val current = player.trackSelectionParameters.preferredTextLanguages.firstOrNull()
    if (languages.isEmpty() || current == languages.lastOrNull()) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        return if (languages.isEmpty()) "Nenhuma legenda disponível" else "Legendas desativadas"
    }
    val next = languages[(languages.indexOf(current) + 1).coerceAtLeast(0) % languages.size]
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(next).build()
    return "Legenda: ${next.uppercase(Locale.ROOT)}"
}

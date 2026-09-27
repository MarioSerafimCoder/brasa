@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Text
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.playback.PlaybackExtrasPolicy
import com.brasa.tv.data.repository.ProgressSyncState
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaIcon
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaSurface
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted

internal data class PlayerOverlayActions(
    val seekBy: (Long) -> Unit,
    val seekToPosition: (Long) -> Unit,
    val saveAt: (Long, Boolean) -> Unit,
    val exit: () -> Unit,
    val retryPlayback: () -> Unit,
    val revealControls: () -> Unit,
    val onNext: (CatalogItem) -> Unit,
    val onSignal: (String, Boolean) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerOverlay(
    info: PlaybackInfo,
    selected: CatalogItem?,
    seriesTitle: String,
    related: List<CatalogItem>,
    serverBaseUrl: String,
    settings: AppSettings,
    container: AppContainer,
    player: ExoPlayer,
    controls: PlayerControlsState,
    seek: PlayerSeekController,
    seekThumbnail: android.graphics.Bitmap?,
    progressStatus: ProgressSyncState,
    error: MutableState<String>,
    actions: PlayerOverlayActions,
) {
    var ended by controls.ended
    var position by controls.position
    var duration by controls.duration
    var buffered by controls.buffered
    var trackNotice by controls.trackNotice
    var centerNotice by controls.centerNotice
    var selectedQuality by controls.selectedQuality
    var trackDialogType by controls.trackDialogType
    var technicalInfoVisible by controls.technicalInfoVisible
    var restoreTrackFocus by controls.restoreTrackFocus
    var currentTracks by controls.currentTracks
    var subtitleDelayMs by controls.subtitleDelayMs
    var controlsVisible by controls.controlsVisible
    var playRequested by controls.playRequested
    val rootFocus = controls.rootFocus
    val playFocus = controls.playFocus
    val errorFocus = controls.errorFocus
    val infoFocus = controls.infoFocus

    var loadError by error
    var timelineFocused by seek.focused
    var seekPreview by seek.preview
    val thumbnailPosition =
        if (seekPreview >= 0) PlaybackExtrasPolicy.previewBucket(seekPreview) else -1L
    fun seekBy(delta: Long) = actions.seekBy(delta)
    fun seekToPosition(target: Long) = actions.seekToPosition(target)
    fun saveAt(target: Long, completed: Boolean = false) = actions.saveAt(target, completed)
    fun exit() = actions.exit()
    fun revealControls() = actions.revealControls()
    fun retryPlayback() = actions.retryPlayback()
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN)
                    return@onPreviewKeyEvent false
                val wasVisible = controlsVisible
                revealControls()
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        seekBy(-10_000)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        seekBy(10_000)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.playWhenReady) {
                            player.pause()
                            centerNotice = "Pausado"
                        } else {
                            player.play()
                            centerNotice = "Reproduzindo"
                        }
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player.pause()
                        centerNotice = "Pausado"
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        centerNotice = "Reproduzindo"
                        true
                    }
                    else -> !wasVisible
                }
            }
            .focusable()
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.player = player
                view.subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setFractionalTextSize(
                        SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * settings.subtitleSize
                    )
                    setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            if (settings.subtitleStyle == "background") 0xB3000000.toInt()
                            else android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null,
                        )
                    )
                }
            },
        )

        if (loadError.isNotBlank()) {
            Column(
                Modifier.align(Alignment.Center)
                    .width(560.dp)
                    .background(BrasaSurface.copy(alpha = .97f), RoundedCornerShape(16.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Falha na reprodução",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(loadError, color = BrasaTextMuted, fontSize = 17.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrasaButton(
                        "Tentar novamente",
                        {
                            player.playWhenReady = true
                            retryPlayback()
                        },
                        Modifier.focusRequester(errorFocus),
                        style = BrasaButtonStyle.Primary,
                    )
                    BrasaButton("Voltar", ::exit)
                }
            }
        }

        if (controlsVisible && loadError.isBlank()) {
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = .55f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = .9f),
                            )
                        )
                    )
            )
            Column(
                Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 34.dp, vertical = 24.dp)
            ) {
                val episodeContext =
                    listOfNotNull(
                            selected?.seasonNumber?.let { "Temporada $it" },
                            selected?.episodeNumber?.let { "Episódio $it" },
                        )
                        .joinToString(" · ")
                Text(
                    seriesTitle.ifBlank {
                        selected?.title.orEmpty().ifBlank { "Reproduzindo agora" }
                    },
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (seriesTitle.isNotBlank()) {
                    Text(
                        listOf(episodeContext, selected?.title.orEmpty())
                            .filter { it.isNotBlank() }
                            .joinToString(" — "),
                        color = BrasaTextMuted,
                        fontSize = 16.sp,
                    )
                }
            }
            PlayerTechnicalInfo(info, controls)
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 52.dp, vertical = 30.dp)
            ) {
                if (trackNotice.isNotBlank()) {
                    Text(
                        trackNotice,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(9.dp))
                }
                val visiblePosition = seekPreview.takeIf { it >= 0 } ?: position
                if (seekPreview >= 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        seekThumbnail?.let { bitmap ->
                            androidx.compose.foundation.Image(
                                bitmap.asImageBitmap(),
                                "Prévia em ${formatTime(thumbnailPosition)}",
                                Modifier.width(192.dp).height(108.dp),
                            )
                        }
                        Text(
                            if (seekThumbnail == null) "Prévia indisponível • vídeo em prioridade"
                            else "Prévia: ${formatTime(thumbnailPosition)}",
                            color = BrasaTextMuted,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val marker = PlaybackExtrasPolicy.activeMarker(info.markers, position, duration)
                if (marker != null) {
                    BrasaButton(
                        if (marker.kind == "intro") "Pular abertura" else "Pular créditos",
                        {
                            if (marker.endMs >= duration - 1000) {
                                saveAt(duration, completed = true)
                                player.pause()
                                ended = true
                            } else seekToPosition(marker.endMs)
                        },
                        style = BrasaButtonStyle.Primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val timelineModifier =
                    Modifier.fillMaxWidth()
                        .onFocusChanged {
                            timelineFocused = it.isFocused
                            if (!it.isFocused) seekPreview = -1L
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN)
                                return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                    seek.movePreview(
                                        position,
                                        duration,
                                        -1,
                                        event.nativeKeyEvent.repeatCount,
                                    )
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                    seek.movePreview(
                                        position,
                                        duration,
                                        1,
                                        event.nativeKeyEvent.repeatCount,
                                    )
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER -> {
                                    seekToPosition(seekPreview.takeIf { it >= 0 } ?: position)
                                    seekPreview = -1L
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable()
                        .then(
                            if (timelineFocused)
                                Modifier.border(2.dp, BrasaOrange, RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            else Modifier
                        )
                Row(timelineModifier, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatTime(visiblePosition),
                        color = if (timelineFocused) BrasaOrange else BrasaText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(13.dp))
                    Box(
                        Modifier.weight(1f)
                            .height(5.dp)
                            .background(Color.White.copy(alpha = .24f), RoundedCornerShape(50))
                    ) {
                        if (duration > 0)
                            Box(
                                Modifier.fillMaxWidth(
                                        (buffered.toFloat() / duration).coerceIn(0f, 1f)
                                    )
                                    .fillMaxHeight()
                                    .background(
                                        Color.White.copy(alpha = .45f),
                                        RoundedCornerShape(50),
                                    )
                            )
                        if (duration > 0)
                            Box(
                                Modifier.fillMaxWidth(
                                        (visiblePosition.toFloat() / duration).coerceIn(0f, 1f)
                                    )
                                    .fillMaxHeight()
                                    .background(BrasaOrange, RoundedCornerShape(50))
                            )
                    }
                    Spacer(Modifier.width(13.dp))
                    Text(formatTime(duration), color = BrasaTextMuted, fontSize = 14.sp)
                }
                Text(
                    if (timelineFocused) "← → escolha o ponto  •  OK para carregar"
                    else "OK abre o ponto escolhido  •  Informações técnicas no botão Informações",
                    color = if (timelineFocused) BrasaOrange else BrasaTextMuted,
                    fontSize = 13.sp,
                )
                if (progressStatus.pending > 0 || progressStatus.message.startsWith("Não foi"))
                    Text(progressStatus.message, color = BrasaTextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(17.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    BrasaButton("10s", { seekBy(-10_000) }, leadingIcon = BrasaIcon.Replay)
                    Spacer(Modifier.width(11.dp))
                    BrasaButton(
                        if (playRequested) "Pausar" else "Reproduzir",
                        {
                            if (player.playWhenReady) {
                                player.pause()
                                centerNotice = "Pausado"
                            } else {
                                player.play()
                                centerNotice = "Reproduzindo"
                            }
                        },
                        Modifier.focusRequester(playFocus),
                        style = BrasaButtonStyle.Primary,
                        leadingIcon = if (playRequested) BrasaIcon.Pause else BrasaIcon.Play,
                    )
                    Spacer(Modifier.width(11.dp))
                    BrasaButton("10s", { seekBy(10_000) }, leadingIcon = BrasaIcon.Forward)
                    Spacer(Modifier.width(22.dp))
                    BrasaButton(
                        "Áudio",
                        {
                            trackDialogType = C.TRACK_TYPE_AUDIO
                            revealControls()
                        },
                    )
                    Spacer(Modifier.width(9.dp))
                    BrasaButton(
                        "Legenda",
                        {
                            trackDialogType = C.TRACK_TYPE_TEXT
                            revealControls()
                        },
                    )
                    if (info.playbackMode == "hls") {
                        Spacer(Modifier.width(9.dp))
                        BrasaButton(
                            selectedQuality,
                            {
                                selectedQuality = cycleQuality(player, info, selectedQuality)
                                trackNotice = "Qualidade: $selectedQuality"
                                revealControls()
                            },
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    BrasaButton(
                        "Informações",
                        {
                            technicalInfoVisible = true
                            revealControls()
                        },
                        Modifier.focusRequester(infoFocus),
                        leadingIcon = BrasaIcon.Info,
                    )
                }
            }
        }

        if (centerNotice.isNotBlank()) {
            Text(
                centerNotice,
                modifier =
                    Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(50))
                        .padding(horizontal = 26.dp, vertical = 14.dp),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        PlayerTracks(
            trackDialogType,
            currentTracks,
            player,
            settings,
            container,
            info,
            serverBaseUrl,
            subtitleDelayMs,
            { subtitleDelayMs = it },
            { centerNotice = it },
        ) {
            trackDialogType = null
            revealControls()
            restoreTrackFocus = true
        }

        PlayerEndPanel(info, selected, related, settings, controls, actions)
    }
}

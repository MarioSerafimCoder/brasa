@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.data.storage.AppSettings
import kotlinx.coroutines.launch

@Composable
internal fun PlayerTracks(
    trackDialogType: Int?,
    currentTracks: Tracks,
    player: ExoPlayer,
    settings: AppSettings,
    container: AppContainer,
    info: PlaybackInfo,
    serverBaseUrl: String,
    subtitleDelayMs: Long,
    onDelayChanged: (Long) -> Unit,
    onNotice: (String) -> Unit,
    onClose: () -> Unit,
) {
    val playbackScope = rememberCoroutineScope()
    trackDialogType?.let { type ->
        TrackSelectionDialog(
            type = type,
            tracks = playbackTracks(currentTracks, type),
            subtitlesDisabled =
                C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes,
            settings = settings,
            onSelect = { track ->
                player.trackSelectionParameters =
                    selectPlaybackTrack(player.trackSelectionParameters, type, track)
                playbackScope.launch {
                    if (type == C.TRACK_TYPE_AUDIO)
                        container.settings.saveAudioLanguage(
                            settings.selectedProfileId,
                            track?.language.orEmpty(),
                        )
                    else
                        container.settings.saveSubtitleChoice(
                            settings.selectedProfileId,
                            if (track == null) "off" else "language",
                            track?.language.orEmpty(),
                        )
                }
                onClose()
            },
            onAutomaticAudio = {
                player.trackSelectionParameters =
                    player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .setPreferredAudioLanguage(null)
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .build()
                playbackScope.launch {
                    container.settings.saveAudioLanguage(settings.selectedProfileId, "")
                }
                onClose()
            },
            subtitleDelayMs = subtitleDelayMs,
            onDelay = { value ->
                playbackScope.launch {
                    try {
                        container.settings.saveSubtitleDelay(
                            serverBaseUrl,
                            settings.selectedProfileId,
                            info.mediaKey,
                            value,
                        )
                        container.playback.setSubtitleDelay(player, info, value)
                        onDelayChanged(value)
                        onNotice("Sincronização de legenda salva.")
                        onClose()
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        onNotice("Não foi possível aplicar o ajuste. Tente novamente.")
                    }
                }
            },
            onSize = { size ->
                playbackScope.launch {
                    container.settings.saveSubtitleSize(settings.selectedProfileId, size)
                }
            },
            onStyle = { style ->
                playbackScope.launch {
                    container.settings.saveSubtitleStyle(settings.selectedProfileId, style)
                }
            },
            onDismiss = onClose,
        )
    }
}

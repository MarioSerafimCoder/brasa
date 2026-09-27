@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.playback.PlaybackExtrasPolicy
import com.brasa.tv.core.playback.PlaybackTimeline
import kotlinx.coroutines.delay

@Composable
internal fun PlayerControlsEffects(
    player: ExoPlayer,
    info: PlaybackInfo,
    controls: PlayerControlsState,
    seek: PlayerSeekController,
    appForeground: Boolean,
    onReveal: () -> Unit,
) {
    var controlsVisible by controls.controlsVisible
    val interaction by controls.interaction
    val isPlaying by controls.isPlaying
    val trackDialogType by controls.trackDialogType
    val timelineFocused by seek.focused
    val technicalInfoVisible by controls.technicalInfoVisible
    val ended by controls.ended
    var position by controls.position
    var duration by controls.duration
    var buffered by controls.buffered
    var trackNotice by controls.trackNotice
    var centerNotice by controls.centerNotice
    val rootFocus = controls.rootFocus
    val playFocus = controls.playFocus
    val revealControls by rememberUpdatedState(onReveal)
    LaunchedEffect(
        controlsVisible,
        interaction,
        isPlaying,
        trackDialogType,
        timelineFocused,
        technicalInfoVisible,
        ended,
    ) {
        if (
            controlsVisible &&
                isPlaying &&
                !ended &&
                trackDialogType == null &&
                !timelineFocused &&
                !technicalInfoVisible
        ) {
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
            buffered =
                PlaybackTimeline.absolutePosition(info, player.bufferedPosition)
                    .coerceAtLeast(position)
            delay(if (controlsVisible) 500 else 1_500)
        }
    }
    val availableSkip = PlaybackExtrasPolicy.activeMarker(info.markers, position, duration)
    LaunchedEffect(availableSkip?.kind, availableSkip?.startMs) {
        if (availableSkip != null && appForeground) revealControls()
    }
    LaunchedEffect(trackNotice) {
        if (trackNotice.isNotBlank()) {
            delay(2_400)
            trackNotice = ""
        }
    }
    LaunchedEffect(centerNotice) {
        if (centerNotice.isNotBlank()) {
            delay(900)
            centerNotice = ""
        }
    }
}

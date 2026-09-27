@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.media3.exoplayer.ExoPlayer
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.playback.PlaybackTimeline

internal class PlayerControlsState(player: ExoPlayer, info: PlaybackInfo) {
    val controlsVisible = mutableStateOf(true)
    val interaction = mutableIntStateOf(0)
    val isPlaying = mutableStateOf(player.isPlaying)
    val playRequested = mutableStateOf(player.playWhenReady)
    val ended = mutableStateOf(false)
    val autoNextSeconds = mutableIntStateOf(10)
    val autoNextCancelled = mutableStateOf(false)
    val position =
        mutableLongStateOf(PlaybackTimeline.absolutePosition(info, player.currentPosition))
    val duration = mutableLongStateOf(PlaybackTimeline.absoluteDuration(info, 0))
    val buffered =
        mutableLongStateOf(PlaybackTimeline.absolutePosition(info, player.bufferedPosition))
    val trackNotice = mutableStateOf("")
    val centerNotice = mutableStateOf("")
    val selectedQuality = mutableStateOf("Automática")
    val actualHeight = mutableIntStateOf(player.videoFormat?.height ?: 0)
    val trackDialogType = mutableStateOf<Int?>(null)
    val technicalInfoVisible = mutableStateOf(false)
    val restoreInfoFocus = mutableStateOf(false)
    val restoreTrackFocus = mutableStateOf(false)
    val currentTracks = mutableStateOf(player.currentTracks)
    val subtitleDelayMs = mutableLongStateOf(info.subtitleDelayMs)
    val rootFocus = FocusRequester()
    val playFocus = FocusRequester()
    val errorFocus = FocusRequester()
    val endFocus = FocusRequester()
    val infoFocus = FocusRequester()
}

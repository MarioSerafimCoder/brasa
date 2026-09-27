@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.playback.PlaybackDiagnosticsRecorder
import com.brasa.tv.core.playback.PlaybackEvent
import com.brasa.tv.core.playback.PlaybackRecovery
import com.brasa.tv.core.playback.PlaybackTimeline
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal class PlayerRecoveryState {
    val retryCount = mutableIntStateOf(0)
    val pendingRetry = mutableStateOf<Job?>(null)
    val fallbackRequested = mutableStateOf(false)
    val firstFrameRendered = mutableStateOf(false)
    val retrySequence = mutableIntStateOf(0)
}

@Composable
internal fun rememberPlaybackRetry(
    player: ExoPlayer,
    state: PlayerRecoveryState,
    recovery: PlaybackRecovery,
    diagnostics: PlaybackDiagnosticsRecorder?,
    appForeground: Boolean,
    error: androidx.compose.runtime.MutableState<String>,
): () -> Unit {
    var pendingRetry by state.pendingRetry
    var firstFrameRendered by state.firstFrameRendered
    var retrySequence by state.retrySequence
    var loadError by error
    val foreground by androidx.compose.runtime.rememberUpdatedState(appForeground)
    fun retryPlayback() {
        if (!foreground) return
        diagnostics?.record(PlaybackEvent(kind = "retry"))
        loadError = ""
        val resumePlayback = player.playWhenReady
        // This is local player time, including when resuming an offset HLS playlist.
        val retryPositionOverride = player.currentPosition.coerceAtLeast(0)
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

    return ::retryPlayback
}

@Composable
internal fun PlaybackRecoveryEffects(
    player: ExoPlayer,
    info: PlaybackInfo,
    state: PlayerRecoveryState,
    recovery: PlaybackRecovery,
    error: androidx.compose.runtime.MutableState<String>,
    appForeground: Boolean,
    recoveryRequested: () -> Boolean,
    ended: () -> Boolean,
    revealControls: () -> Unit,
    retryPlayback: () -> Unit,
    remoteSeek: (Long) -> Unit,
) {
    var retryCount by state.retryCount
    var pendingRetry by state.pendingRetry
    var fallbackRequested by state.fallbackRequested
    var firstFrameRendered by state.firstFrameRendered
    var retrySequence by state.retrySequence
    var loadError by error
    val foreground by androidx.compose.runtime.rememberUpdatedState(appForeground)
    LaunchedEffect(player, info.playbackMode, retrySequence) {
        val startup = PlaybackRecovery()
        while (!firstFrameRendered && !recoveryRequested() && loadError.isBlank()) {
            delay(2_000)
            val waitingForFrame =
                foreground &&
                    player.playWhenReady &&
                    player.playerError == null &&
                    player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                    pendingRetry?.isActive != true &&
                    player.playbackState in setOf(Player.STATE_BUFFERING, Player.STATE_READY)
            if (
                !startup.sample(
                    SystemClock.elapsedRealtime(),
                    0,
                    waitingForFrame,
                    buffering = player.playbackState == Player.STATE_BUFFERING,
                    bufferedPositionMs = player.bufferedPosition,
                )
            )
                continue
            if ((info.playbackMode == "direct" || info.videoCopied) && !fallbackRequested) {
                fallbackRequested = true
                loadError = ""
                remoteSeek(PlaybackTimeline.absolutePosition(info, player.currentPosition))
                Log.w(
                    TAG,
                    "Fallback HLS solicitado: buffer recebido sem primeiro quadro para ${info.mediaKey}",
                )
            } else if (recovery.beginRetry()) {
                retryCount = recovery.attempts
                retryPlayback()
                Log.w(
                    TAG,
                    "Player recriado: buffer recebido sem primeiro quadro para ${info.mediaKey}",
                )
            } else {
                loadError =
                    "A TV recebeu o vídeo, mas não conseguiu exibir o primeiro quadro. Tente novamente."
                player.pause()
                Log.e(
                    TAG,
                    "Retomada sem primeiro quadro após $retryCount tentativas para ${info.mediaKey}",
                )
            }
            break
        }
    }
    LaunchedEffect(player, firstFrameRendered, retrySequence) {
        if (!firstFrameRendered) return@LaunchedEffect
        recovery.resetSampling()
        while (!recoveryRequested() && !ended()) {
            delay(2_000)
            val currentPosition = player.currentPosition
            val shouldAdvance =
                foreground &&
                    player.playWhenReady &&
                    player.playbackState != Player.STATE_ENDED &&
                    player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                    player.playerError == null &&
                    pendingRetry?.isActive != true &&
                    loadError.isBlank()
            if (
                recovery.sample(
                    SystemClock.elapsedRealtime(),
                    currentPosition,
                    shouldAdvance,
                    buffering = player.playbackState == Player.STATE_BUFFERING,
                    bufferedPositionMs = player.bufferedPosition,
                )
            ) {
                val absolute = PlaybackTimeline.absolutePosition(info, currentPosition)
                Log.w(
                    TAG,
                    "Reprodução sem avanço em ${info.mediaKey}: position=$absolute, state=${player.playbackState}, isPlaying=${player.isPlaying}",
                )
                if (recovery.beginRetry()) {
                    retryCount = recovery.attempts
                    retryPlayback()
                } else if (recovery.canRecoverPrematureEnd(absolute)) {
                    remoteSeek(absolute)
                } else {
                    loadError =
                        "Não foi possível continuar neste trecho. Confira a conexão e se o arquivo do vídeo está completo no computador."
                    player.pause()
                    revealControls()
                }
                break
            }
        }
    }
}

private const val TAG = "BRasaPlayback"

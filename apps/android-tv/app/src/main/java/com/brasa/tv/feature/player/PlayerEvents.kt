@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.playback.PlaybackErrorAction
import com.brasa.tv.core.playback.PlaybackErrorPolicy
import com.brasa.tv.core.playback.PlaybackRecovery
import com.brasa.tv.core.playback.PlaybackTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Player callbacks share the active controls but always invoke the latest screen actions. */
@Composable
internal fun rememberPlayerListener(
    player: ExoPlayer,
    info: PlaybackInfo,
    container: AppContainer,
    controls: PlayerControlsState,
    recoveryState: PlayerRecoveryState,
    recovery: PlaybackRecovery,
    error: MutableState<String>,
    foreground: Boolean,
    playbackScope: CoroutineScope,
    onSave: (Boolean) -> Unit,
    onRecover: (Long, Boolean) -> Unit,
    onReveal: () -> Unit,
    onRetry: () -> Unit,
    reportConnection: (com.brasa.tv.core.network.ConnectionProblem) -> Unit,
): Player.Listener {
    var currentTracks by controls.currentTracks
    var playRequested by controls.playRequested
    var isPlaying by controls.isPlaying
    var ended by controls.ended
    var controlsVisible by controls.controlsVisible
    var firstFrameRendered by recoveryState.firstFrameRendered
    var fallbackRequested by recoveryState.fallbackRequested
    var pendingRetry by recoveryState.pendingRetry
    var retryCount by recoveryState.retryCount
    var loadError by error
    val appForeground by rememberUpdatedState(foreground)
    val latestSave by rememberUpdatedState(onSave)
    val latestRecover by rememberUpdatedState(onRecover)
    val revealControls by rememberUpdatedState(onReveal)
    val retryPlayback by rememberUpdatedState(onRetry)
    val onConnectionProblem by rememberUpdatedState(reportConnection)
    fun save(completed: Boolean = false) = latestSave(completed)
    fun requestRemoteSeek(
        target: Long,
        recovery: Boolean = true,
        forceConversion: Boolean = recovery,
    ) = latestRecover(target, forceConversion)
    return remember(player) {
        val startedAt = container.playback.startedAt(player)
        var ready = false
        var rebufferStartedAt = 0L
        var rebufferCount = 0
        object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playRequested = playWhenReady
            }

            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
                revealControls()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val now = SystemClock.elapsedRealtime()
                if (playbackState == Player.STATE_BUFFERING && ready && rebufferStartedAt == 0L)
                    rebufferStartedAt = now
                if (playbackState == Player.STATE_READY) {
                    if (!ready) Log.i(TAG, "STATE_READY ${info.mediaKey} em ${now - startedAt}ms")
                    ready = true
                    if (rebufferStartedAt > 0L) {
                        rebufferCount++
                        Log.i(
                            TAG,
                            "Rebuffer #$rebufferCount ${info.mediaKey}: ${now - rebufferStartedAt}ms",
                        )
                        rebufferStartedAt = 0L
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    val absolute = PlaybackTimeline.absolutePosition(info, player.currentPosition)
                    val total =
                        PlaybackTimeline.absoluteDuration(
                            info,
                            player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L,
                        )
                    if (PlaybackRecovery.isPrematureEnd(absolute, total)) {
                        save()
                        Log.w(
                            TAG,
                            "Mídia terminou antes da duração esperada em ${info.mediaKey}: $absolute/$total, modo=${info.playbackMode}",
                        )
                        if (recovery.canRecoverPrematureEnd(absolute))
                            requestRemoteSeek(absolute, recovery = true)
                        else {
                            loadError =
                                "O vídeo termina antes do esperado neste trecho. O arquivo pode estar incompleto ou danificado; confira a cópia no computador."
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
                Log.i(
                    TAG,
                    "Primeiro frame ${info.mediaKey} em ${SystemClock.elapsedRealtime() - startedAt}ms",
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                val decision =
                    PlaybackErrorPolicy.decide(error, info.playbackMode == "hls", info.videoCopied)
                val httpError =
                    PlaybackErrorPolicy.cause<
                        androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                    >(
                        error
                    )
                if (httpError?.responseCode == 401)
                    onConnectionProblem(com.brasa.tv.core.network.ConnectionProblem.REVOKED)
                if (httpError?.responseCode == 403)
                    onConnectionProblem(com.brasa.tv.core.network.ConnectionProblem.FORBIDDEN)
                if (
                    error.errorCode in
                        setOf(
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                        ) && !container.networkAccess.isLocalNetworkAvailable()
                )
                    onConnectionProblem(com.brasa.tv.core.network.ConnectionProblem.NO_NETWORK)
                when {
                    !appForeground -> {
                        loadError = decision.message
                        player.pause()
                    }
                    decision.action == PlaybackErrorAction.TRANSCODE && !fallbackRequested -> {
                        fallbackRequested = true
                        loadError = ""
                        requestRemoteSeek(
                            PlaybackTimeline.absolutePosition(info, player.currentPosition),
                            recovery = true,
                        )
                    }
                    decision.action == PlaybackErrorAction.RENEW_STREAM &&
                        recovery.beginSourceRenewal() -> {
                        loadError = ""
                        requestRemoteSeek(
                            PlaybackTimeline.absolutePosition(info, player.currentPosition),
                            recovery = true,
                            forceConversion = false,
                        )
                    }
                    decision.action == PlaybackErrorAction.RETRY &&
                        pendingRetry?.isActive == true -> Unit
                    decision.action == PlaybackErrorAction.RETRY && recovery.beginRetry() -> {
                        retryCount = recovery.attempts
                        loadError = ""
                        pendingRetry =
                            playbackScope.launch {
                                delay((decision.delayMs * retryCount).coerceAtMost(30_000))
                                retryPlayback()
                            }
                    }
                    else -> {
                        loadError = decision.message
                        controlsVisible = true
                        player.pause()
                        if (
                            error.errorCode in
                                setOf(
                                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                                )
                        )
                            onConnectionProblem(
                                if (container.networkAccess.isLocalNetworkAvailable())
                                    com.brasa.tv.core.network.ConnectionProblem.SERVER_UNAVAILABLE
                                else com.brasa.tv.core.network.ConnectionProblem.NO_NETWORK
                            )
                    }
                }
                Log.w(TAG, "Falha de reprodução: ${error.errorCodeName}; ação=${decision.action}")
            }
        }
    }
}

private const val TAG = "BRasaPlayback"

@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.playback.PlaybackExtrasPolicy
import com.brasa.tv.core.playback.PlaybackTimeline
import com.brasa.tv.data.storage.AppSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun PlayerNextPreparation(
    player: ExoPlayer,
    info: PlaybackInfo,
    settings: AppSettings,
    appForeground: Boolean,
    container: AppContainer,
    serverBaseUrl: String,
    seek: PlayerSeekController,
) {
    val seekPreview by seek.preview
    val recoveryRequested by seek.requested
    LaunchedEffect(player, info.nextEpisode?.mediaKey, settings.autoplayNext, appForeground) {
        val next = info.nextEpisode ?: return@LaunchedEffect
        val profileId = settings.selectedProfileId
        if (!appForeground || !settings.autoplayNext || profileId.isBlank()) return@LaunchedEffect
        var stableSince = SystemClock.elapsedRealtime()
        var attempted = false
        var preparation: Job? = null
        try {
            while (true) {
                delay(250)
                val now = SystemClock.elapsedRealtime()
                val bufferMs = player.totalBufferedDuration
                if (
                    !player.isPlaying ||
                        player.playbackState == Player.STATE_BUFFERING ||
                        bufferMs < 30_000
                )
                    stableSince = now
                val remaining =
                    PlaybackTimeline.absoluteDuration(info, player.duration.coerceAtLeast(0)) -
                        PlaybackTimeline.absolutePosition(info, player.currentPosition)
                val allowed =
                    PlaybackExtrasPolicy.canPrepareNext(
                        appForeground,
                        player.isPlaying,
                        player.isLoading,
                        player.playbackState == Player.STATE_BUFFERING,
                        bufferMs,
                        remaining,
                        now - stableSince,
                        seekPreview >= 0 || recoveryRequested,
                    )
                if (!allowed) {
                    preparation?.cancel()
                    preparation = null
                    container.playback.cancelNextPreparation()
                } else if (!attempted) {
                    attempted = true
                    preparation = launch {
                        runCatching {
                            val ready =
                                container.repository.playback(
                                    profileId,
                                    next.mediaKey,
                                    prepare = false,
                                )
                            if (
                                isActive &&
                                    player.isPlaying &&
                                    !player.isLoading &&
                                    player.totalBufferedDuration >= 30_000
                            )
                                container.playback.prepareNext(serverBaseUrl, ready)
                        }
                    }
                }
            }
        } finally {
            preparation?.cancel()
            container.playback.cancelNextPreparation()
        }
    }
}

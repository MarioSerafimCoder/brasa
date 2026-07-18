package com.brasa.tv.core.playback

import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.model.WatchProgress

object PlaybackTimeline {
    fun absolutePosition(info: PlaybackInfo, playerPosition: Long): Long {
        val position = info.playbackOffset.coerceAtLeast(0) + playerPosition.coerceAtLeast(0)
        return info.duration?.takeIf { it > 0 }?.let(position::coerceAtMost) ?: position
    }

    fun absoluteDuration(info: PlaybackInfo, playerDuration: Long): Long =
        info.duration?.takeIf { it > 0 }
            ?: (info.playbackOffset.coerceAtLeast(0) + playerDuration.coerceAtLeast(0))

    fun progress(info: PlaybackInfo, playerPosition: Long, playerDuration: Long, completed: Boolean): WatchProgress? {
        val total = absoluteDuration(info, playerDuration)
        if (total <= 0) return null
        val current = absolutePosition(info, playerPosition)
        return WatchProgress(
            mediaType = if (info.mediaKey.startsWith("episode:")) "episode" else "movie",
            mediaId = info.mediaId,
            currentTime = current / 1000.0,
            duration = total / 1000.0,
            percentage = (current.toDouble() / total * 100).coerceIn(0.0, 100.0),
            completed = completed,
        )
    }
}

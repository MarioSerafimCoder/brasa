package com.brasa.tv.core.playback

import com.brasa.tv.core.model.PlaybackMarker
import com.brasa.tv.core.model.PlaybackCapabilities

object PlaybackExtrasPolicy {
    fun stabilityCapabilities(value: PlaybackCapabilities, enabled: Boolean): PlaybackCapabilities = if (!enabled) value else value.copy(
        maxWidth = minOf(value.maxWidth.takeIf { it > 0 } ?: 1280, 1280),
        maxHeight = minOf(value.maxHeight.takeIf { it > 0 } ?: 720, 720),
    )
    fun previewBucket(positionMs: Long) = positionMs.coerceAtLeast(0) / 10_000 * 10_000
    fun canPrepareNext(foreground: Boolean, playing: Boolean, loading: Boolean, buffering: Boolean,
                       bufferedMs: Long, remainingMs: Long, stableMs: Long, seeking: Boolean): Boolean =
        foreground && playing && !loading && !buffering && !seeking && bufferedMs >= maxOf(30_000, remainingMs) &&
            remainingMs in 15_000..120_000 && stableMs >= 30_000
    fun activeMarker(markers: List<PlaybackMarker>, positionMs: Long, durationMs: Long): PlaybackMarker? =
        markers.firstOrNull { it.source in setOf("embedded-chapter", "manual") && it.kind in setOf("intro", "credits") &&
            it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= durationMs &&
            positionMs >= it.startMs && positionMs < it.endMs - 1000 }
}

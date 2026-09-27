package com.brasa.tv.feature.player

import androidx.compose.runtime.*
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.playback.PlaybackExtrasPolicy
import com.brasa.tv.core.playback.SeekThumbnailLoader
import kotlinx.coroutines.delay

/** Absolute UI time stays separate from the local time of an offset HLS playlist. */
internal class PlayerSeekController {
    val preview = mutableLongStateOf(-1L)
    val remoteTarget = mutableLongStateOf(-1L)
    val requested = mutableStateOf(false)
    val focused = mutableStateOf(false)

    fun movePreview(position: Long, duration: Long, direction: Int, repeat: Int) {
        val step =
            when {
                repeat >= 8 -> 120_000L
                repeat >= 3 -> 30_000L
                else -> 10_000L
            }
        val current = preview.longValue.takeIf { it >= 0 } ?: position
        preview.longValue =
            (current + step * direction).coerceIn(0, (duration - 1_000).coerceAtLeast(0))
    }

    fun beginRemoteSeek(target: Long, duration: Long): Long? {
        if (requested.value) return null
        val clamped = target.coerceIn(0, (duration - 1_000).coerceAtLeast(0))
        requested.value = true
        remoteTarget.longValue = clamped
        return clamped
    }
}

@Composable
internal fun rememberSeekThumbnail(
    loader: SeekThumbnailLoader,
    baseUrl: String,
    info: PlaybackInfo,
    previewPosition: Long,
    foreground: Boolean,
    safeToLoad: () -> Boolean,
): android.graphics.Bitmap? {
    var thumbnail by
        remember(loader, info.mediaKey) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val bucket =
        if (previewPosition >= 0) PlaybackExtrasPolicy.previewBucket(previewPosition) else -1L
    LaunchedEffect(loader, baseUrl, info.thumbnailPath, bucket, foreground) {
        thumbnail = null
        if (!foreground || bucket < 0 || info.thumbnailPath.isBlank()) return@LaunchedEffect
        delay(350)
        thumbnail = loader.load(baseUrl, info.thumbnailPath, bucket, safeToLoad())
    }
    return thumbnail
}

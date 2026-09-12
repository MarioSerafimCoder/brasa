@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.core.playback

import android.content.Context
import android.app.ActivityManager
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.core.network.LocalServerAddress

class PlaybackFactory(
    private val context: Context,
    private val http: BrasaHttpClient,
) {
    private companion object {
        const val TAG = "BRasaPlayback"
    }

    fun create(baseUrl: String, info: PlaybackInfo, cache: SimpleCache?, autoPlay: Boolean): ExoPlayer {
        val upstream = http.authenticatedMediaDataSource(baseUrl)
        val dataSource = if (info.playbackMode == "hls") upstream else CacheDataSource.Factory()
            .setCache(requireNotNull(cache) { "Cache progressivo indisponível." })
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(object : CacheDataSource.EventListener {
                override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) { Log.d(TAG, "Cache hit ${info.mediaKey}: $cachedBytesRead bytes; total=$cacheSizeBytes") }
                override fun onCacheIgnored(reason: Int) { Log.w(TAG, "Cache ignorado ${info.mediaKey}: motivo=$reason") }
            })
        val subtitles = info.subtitles.map {
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(LocalServerAddress.resolve(baseUrl, it.src)))
                .setMimeType(it.mimeType.ifBlank { MimeTypes.TEXT_VTT })
                .setLanguage(it.srclang)
                .setLabel(it.label)
                .setSelectionFlags(if (it.default) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val mediaItemBuilder = MediaItem.Builder()
            .setMediaId(info.mediaKey)
            .setUri(LocalServerAddress.resolve(baseUrl, info.playbackUrl))
            .setMimeType(info.mimeType)
            .setSubtitleConfigurations(subtitles)
        if (info.playbackMode != "hls") mediaItemBuilder.setCustomCacheKey(cacheKey(baseUrl, info))
        val mediaItem = mediaItemBuilder.build()
        val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val buffer = PlaybackBufferPolicy.select(info.playbackMode, info.bitrate, info.height, memory.memoryClass, memory.isLowRamDevice)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                buffer.minMs,
                buffer.maxMs,
                buffer.startMs,
                buffer.rebufferMs,
            )
            .setTargetBufferBytes(buffer.targetBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(buffer.backBufferMs, false)
            .build()
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(context, renderers)
            .setTrackSelector(DefaultTrackSelector(context, adaptiveTrackSelectionFactory()))
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6)))
            .build()
            .apply {
                setMediaItem(mediaItem)
                seekTo(info.resumePosition)
                playWhenReady = autoPlay
                prepare()
            }
    }

    fun cacheKey(baseUrl: String, info: PlaybackInfo) =
        "${LocalServerAddress.normalize(baseUrl)}|${info.mediaKey}|${info.playbackMode}|${info.playbackRevision}|${info.playbackUrl}"

    fun playbackIdentity(baseUrl: String, info: PlaybackInfo) = cacheKey(baseUrl, info)

}

// Leave network headroom and require a stable buffer before raising quality.
internal fun adaptiveTrackSelectionFactory() = AdaptiveTrackSelection.Factory(15_000, 25_000, 25_000, 0.70f)

@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.core.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.core.network.LocalServerAddress

class PlaybackFactory(
    private val context: Context,
    private val http: BrasaHttpClient,
) {
    private companion object {
        const val TAG = "BRasaPlayback"
        const val MIN_BUFFER_MS = 20_000
        const val MAX_BUFFER_MS = 45_000
        const val BUFFER_FOR_PLAYBACK_MS = 1_200
        const val BUFFER_AFTER_REBUFFER_MS = 4_000
        const val BACK_BUFFER_MS = 15_000
    }

    fun create(baseUrl: String, info: PlaybackInfo, cache: SimpleCache, autoPlay: Boolean): ExoPlayer {
        val dataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(http.authenticatedMediaDataSource(baseUrl))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(object : CacheDataSource.EventListener {
                override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                    Log.d(TAG, "Cache hit ${info.mediaKey}: $cachedBytesRead bytes; total=$cacheSizeBytes")
                }
                override fun onCacheIgnored(reason: Int) {
                    Log.w(TAG, "Cache ignorado ${info.mediaKey}: motivo=$reason")
                }
            })
        val subtitles = info.subtitles.map {
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(LocalServerAddress.resolve(baseUrl, it.src)))
                .setMimeType(it.mimeType.ifBlank { MimeTypes.TEXT_VTT })
                .setLanguage(it.srclang)
                .setLabel(it.label)
                .setSelectionFlags(if (it.default) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(info.mediaKey)
            .setUri(LocalServerAddress.resolve(baseUrl, info.playbackUrl))
            .setCustomCacheKey(cacheKey(baseUrl, info.mediaKey))
            .setMimeType(info.mimeType)
            .setSubtitleConfigurations(subtitles)
            .build()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_AFTER_REBUFFER_MS,
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(BACK_BUFFER_MS, true)
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
            .apply {
                setMediaItem(mediaItem)
                seekTo(info.resumePosition)
                playWhenReady = autoPlay
                prepare()
            }
    }

    fun cacheKey(baseUrl: String, mediaKey: String) = "${LocalServerAddress.normalize(baseUrl)}|$mediaKey"
}

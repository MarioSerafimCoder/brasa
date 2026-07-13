@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.core.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.core.network.LocalServerAddress
import com.brasa.tv.core.security.SecureTokenStore

class PlaybackFactory(
    private val context: Context,
    private val http: BrasaHttpClient,
    private val tokens: SecureTokenStore,
) {
    fun create(baseUrl: String, info: PlaybackInfo): ExoPlayer {
        val token = tokens.load()?.deviceToken ?: error("Dispositivo sem autorização.")
        val dataSource = OkHttpDataSource.Factory(http.authenticatedClient())
            .setDefaultRequestProperties(mapOf("X-BRasa-Device-Token" to token))
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
            .setMimeType(info.mimeType)
            .setSubtitleConfigurations(subtitles)
            .build()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                6_000,
                30_000,
                700,
                1_800,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
            .apply {
                setMediaItem(mediaItem)
                prepare()
                seekTo(info.resumePosition)
                playWhenReady = true
            }
    }
}

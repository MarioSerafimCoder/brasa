@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.core.playback

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.core.network.LocalServerAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** No second player/decoder. Cache at most 512 KiB of one ready original, never start HLS. */
class NextEpisodePreparer(private val http: BrasaHttpClient, private val cache: PlaybackCache) {
    @Volatile private var writer: CacheWriter? = null
    @Volatile private var cancelled = true
    private val busy = AtomicBoolean(false)
    fun cancel() { cancelled = true; writer?.cancel() }
    suspend fun prepare(base: String, info: PlaybackInfo, cacheKey: String) {
        if (info.playbackMode != "direct" || !info.supportsRange || info.preparationStatus != "ready" || !busy.compareAndSet(false, true)) return
        cancelled = false
        try {
            val simpleCache = cache.getOrCreate()
            withContext(Dispatchers.IO) {
                if (cancelled) return@withContext
                val source = CacheDataSource.Factory().setCache(simpleCache)
                    .setUpstreamDataSourceFactory(http.authenticatedMediaDataSource(base, background = true)).createDataSource()
                val spec = nextEpisodeDataSpec(base, info.playbackUrl, cacheKey)
                val task = CacheWriter(source, spec, ByteArray(16 * 1024), null)
                writer = task
                if (cancelled) task.cancel()
                try { task.cache() } finally { writer = null }
            }
        } finally { busy.set(false) }
    }
}

internal fun nextEpisodeDataSpec(base: String, path: String, cacheKey: String): DataSpec =
    DataSpec.Builder().setUri(LocalServerAddress.resolve(base, path)).setKey(cacheKey).setLength(512L * 1024).build()

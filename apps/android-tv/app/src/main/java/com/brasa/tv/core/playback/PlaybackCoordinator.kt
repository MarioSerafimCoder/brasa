@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.core.playback

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.network.BrasaHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackCoordinator(
    context: Context,
    private val http: BrasaHttpClient,
    private val cache: PlaybackCache,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var current: Holder? = null
    private var preloadMonitor: Job? = null
    private var generation = 0L

    suspend fun preload(baseUrl: String, info: PlaybackInfo) {
        val requestGeneration = ++generation
        val simpleCache = cache.getOrCreate()
        withContext(Dispatchers.Main.immediate) {
            if (requestGeneration != generation) return@withContext
            val factory = PlaybackFactory(appContext, http)
            val cacheKey = factory.cacheKey(baseUrl, info)
            val existing = current
            if (existing?.matches(cacheKey) == true) return@withContext
            releaseCurrent()
            val player = factory.create(baseUrl, info, simpleCache, autoPlay = false)
            current = Holder(baseUrl, info.mediaKey, cacheKey, player, preloading = true)
            monitorPreload(player, info.mediaKey)
            Log.d(TAG, "Preload iniciado: ${info.mediaKey}")
        }
    }

    suspend fun acquire(baseUrl: String, info: PlaybackInfo): ExoPlayer {
        val requestGeneration = ++generation
        val simpleCache = cache.getOrCreate()
        return withContext(Dispatchers.Main.immediate) {
            check(requestGeneration == generation) { "Preparação de mídia substituída." }
            val factory = PlaybackFactory(appContext, http)
            val cacheKey = factory.cacheKey(baseUrl, info)
            val existing = current
            if (existing?.matches(cacheKey) == true) {
                preloadMonitor?.cancel()
                preloadMonitor = null
                existing.preloading = false
                existing.startedAtMs = SystemClock.elapsedRealtime()
                if (existing.player.playbackState == Player.STATE_IDLE) {
                    existing.player.seekTo(info.resumePosition)
                    existing.player.prepare()
                }
                existing.player.playWhenReady = true
                Log.d(TAG, "Player de preload reutilizado: ${info.mediaKey}")
                return@withContext existing.player
            }
            releaseCurrent()
            val player = factory.create(baseUrl, info, simpleCache, autoPlay = true)
            current = Holder(
                baseUrl,
                info.mediaKey,
                cacheKey,
                player,
                preloading = false,
                startedAtMs = SystemClock.elapsedRealtime(),
            )
            player
        }
    }

    fun cancelPreload() {
        generation++
        scope.launch {
            if (current?.preloading == true) releaseCurrent()
        }
    }

    fun release(player: ExoPlayer, completed: Boolean = false) {
        scope.launch {
            val holder = current
            if (holder?.player === player) {
                val cacheKey = holder.cacheKey
                releaseCurrent()
                if (completed) scheduleCompletedRemoval(cacheKey)
            } else {
                player.stop()
                player.release()
            }
        }
    }

    fun startedAt(player: ExoPlayer): Long = current?.takeIf { it.player === player }?.startedAtMs ?: SystemClock.elapsedRealtime()

    fun trimInactiveCache() {
        val active = current?.cacheKey
        scope.launch { cache.trimInactive(active) }
    }

    suspend fun cacheSizeBytes(): Long = cache.sizeBytes()

    suspend fun clearInactiveCache() {
        val active = current?.cacheKey
        cache.trimInactive(active)
    }

    fun clear() {
        generation++
        scope.launch {
            releaseCurrent()
            cache.clear()
        }
    }

    private fun monitorPreload(player: ExoPlayer, mediaKey: String) {
        preloadMonitor?.cancel()
        preloadMonitor = scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            while (current?.player === player && current?.preloading == true) {
                val bufferedMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
                val timedOut = SystemClock.elapsedRealtime() - startedAt >= PRELOAD_TIMEOUT_MS
                if (bufferedMs >= PRELOAD_TARGET_MS || timedOut || player.playerError != null) {
                    player.stop()
                    Log.d(TAG, "Preload limitado: $mediaKey, buffer=${bufferedMs}ms")
                    return@launch
                }
                delay(PRELOAD_POLL_MS)
            }
        }
    }

    private fun releaseCurrent() {
        preloadMonitor?.cancel()
        preloadMonitor = null
        current?.player?.run {
            playWhenReady = false
            stop()
            release()
        }
        current = null
    }

    private fun scheduleCompletedRemoval(cacheKey: String) {
        scope.launch {
            delay(COMPLETED_CACHE_GRACE_MS)
            if (current?.cacheKey != cacheKey) cache.remove(cacheKey)
        }
    }

    private data class Holder(
        val baseUrl: String,
        val mediaKey: String,
        val cacheKey: String,
        val player: ExoPlayer,
        var preloading: Boolean,
        var startedAtMs: Long = 0,
    ) {
        fun matches(candidateCacheKey: String) = cacheKey == candidateCacheKey
    }

    private companion object {
        const val TAG = "BRasaPlayback"
        const val PRELOAD_TARGET_MS = 3_000L
        const val PRELOAD_TIMEOUT_MS = 6_000L
        const val PRELOAD_POLL_MS = 100L
        const val COMPLETED_CACHE_GRACE_MS = 2 * 60_000L
    }
}

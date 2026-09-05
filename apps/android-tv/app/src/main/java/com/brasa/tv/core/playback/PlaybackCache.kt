@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.core.playback

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PlaybackCache(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.cacheDir, "media3-playback")
    private val lock = Any()
    @Volatile private var instance: SimpleCache? = null

    suspend fun getOrCreate(): SimpleCache = withContext(Dispatchers.IO) {
        instance ?: synchronized(lock) {
            instance ?: createSafely().also { instance = it }
        }
    }

    suspend fun remove(cacheKey: String) = withContext(Dispatchers.IO) {
        runCatching { instance?.removeResource(cacheKey) }
            .onFailure { Log.w(TAG, "Falha ao remover mídia do cache: ${it.message}") }
        Unit
    }

    suspend fun trimInactive(activeCacheKey: String? = null) = withContext(Dispatchers.IO) {
        val cache = instance ?: return@withContext
        cache.keys.filter { it != activeCacheKey }.forEach { key ->
            runCatching { cache.removeResource(key) }
        }
    }

    suspend fun clear() = trimInactive()

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        if (!directory.exists()) 0L else directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun createSafely(): SimpleCache {
        fun create() = SimpleCache(
            directory,
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(appContext),
        )
        return runCatching(::create).getOrElse { firstError ->
            Log.w(TAG, "Recriando cache temporário inválido: ${firstError.message}")
            directory.deleteRecursively()
            create()
        }
    }

    private companion object {
        const val TAG = "BRasaCache"
        const val MAX_CACHE_BYTES = 512L * 1024 * 1024
    }
}

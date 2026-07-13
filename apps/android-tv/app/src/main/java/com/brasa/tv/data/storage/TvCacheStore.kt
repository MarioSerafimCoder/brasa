package com.brasa.tv.data.storage

import android.content.Context
import com.brasa.tv.core.model.HomeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class CachedHome(val serverBaseUrl: String, val profileId: String, val savedAt: Long, val value: HomeResponse)

class TvCacheStore(context: Context, private val json: Json) {
    private val file = File(context.filesDir, "tv-home-cache.json")

    suspend fun home(serverBaseUrl: String, profileId: String): HomeResponse? = withContext(Dispatchers.IO) {
        val cached = runCatching { json.decodeFromString<CachedHome>(file.readText()) }.getOrNull() ?: return@withContext null
        cached.value.takeIf { cached.serverBaseUrl == serverBaseUrl && cached.profileId == profileId && System.currentTimeMillis() - cached.savedAt < MAX_AGE_MS }
    }

    suspend fun saveHome(serverBaseUrl: String, profileId: String, value: HomeResponse) = withContext(Dispatchers.IO) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.encodeToString(CachedHome(serverBaseUrl, profileId, System.currentTimeMillis(), value)))
        if (!temporary.renameTo(file)) { file.writeText(temporary.readText()); temporary.delete() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete(); Unit }

    private companion object { const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 }
}

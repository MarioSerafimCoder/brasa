package com.brasa.tv.data.storage

import android.content.Context
import com.brasa.tv.core.model.HomeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
private data class CachedHome(val serverBaseUrl: String, val profileId: String, val savedAt: Long, val value: HomeResponse)

class TvCacheStore(context: Context, private val json: Json) {
    private val directory = File(context.filesDir, "tv-home-cache")
    private fun cacheFile(serverBaseUrl: String, profileId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest("$serverBaseUrl\u0000$profileId".toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.json")
    }

    suspend fun home(serverBaseUrl: String, profileId: String): HomeResponse? = withContext(Dispatchers.IO) {
        val file = cacheFile(serverBaseUrl, profileId)
        if (!file.isFile) return@withContext null
        val cached = runCatching { json.decodeFromString<CachedHome>(file.readText()) }.getOrElse {
            file.delete()
            return@withContext null
        }
        cached.value.takeIf { cached.serverBaseUrl == serverBaseUrl && cached.profileId == profileId && System.currentTimeMillis() - cached.savedAt < MAX_AGE_MS }
    }

    suspend fun saveHome(serverBaseUrl: String, profileId: String, value: HomeResponse) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val file = cacheFile(serverBaseUrl, profileId)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.encodeToString(CachedHome(serverBaseUrl, profileId, System.currentTimeMillis(), value)))
        if (!temporary.renameTo(file)) { file.writeText(temporary.readText()); temporary.delete() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { directory.deleteRecursively(); Unit }

    private companion object { const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 }
}

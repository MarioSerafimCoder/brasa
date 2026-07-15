package com.brasa.tv.data.repository

import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.CatalogResponse
import com.brasa.tv.core.model.DeviceSession
import com.brasa.tv.core.model.HomeResponse
import com.brasa.tv.core.model.HomeRow
import com.brasa.tv.core.model.PairingStatus
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.model.Season
import com.brasa.tv.core.model.ServerInfo
import com.brasa.tv.core.model.WatchProgress
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.core.network.LocalServerAddress
import com.brasa.tv.core.security.SecureTokenStore
import com.brasa.tv.data.api.BrasaApi
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.data.storage.AppSettingsStore
import com.brasa.tv.data.storage.TvCacheStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

class BrasaRepository(
    private val api: BrasaApi,
    private val settings: AppSettingsStore,
    private val tokens: SecureTokenStore,
    private val http: BrasaHttpClient,
    private val cache: TvCacheStore,
) {
    private val playbackCache = ConcurrentHashMap<String, Pair<Long, PlaybackInfo>>()
    @Volatile private var profileCache: Pair<Long, List<com.brasa.tv.core.model.Profile>>? = null

    suspend fun settings(): AppSettings = settings.values.first()
    suspend fun serverBaseUrl(): String = requireServer()

    suspend fun connect(rawAddress: String): ServerInfo {
        val base = LocalServerAddress.normalize(rawAddress)
        val info = api.bootstrap(base)
        require(info.apiVersion == 1) { "Servidor incompatível." }
        settings.saveServer(base, info.name)
        http.bindServer(base)
        return info
    }

    suspend fun restore(): ServerInfo? {
        val current = settings()
        if (current.serverBaseUrl.isBlank() || !tokens.hasToken()) return null
        http.bindServer(current.serverBaseUrl)
        return runCatching {
            coroutineScope {
                val info = async { api.bootstrap(current.serverBaseUrl) }
                val profiles = async { api.profiles(current.serverBaseUrl) }
                val loadedProfiles = profiles.await()
                profileCache = System.currentTimeMillis() to loadedProfiles
                info.await()
            }
        }.getOrNull()
    }

    suspend fun pair(deviceName: String, onStatus: suspend (PairingStatus) -> Unit): DeviceSession {
        val base = requireServer()
        val request = api.startPairing(base, deviceName)
        onStatus(PairingStatus(requestId = request.requestId, code = request.code, status = "pending", expiresAt = request.expiresAt, remainingMs = request.remainingMs))
        while (true) {
            delay(2_000)
            val status = api.pairingStatus(base, request.requestId)
            onStatus(status.copy(code = request.code))
            when (status.status) {
                "approved" -> {
                    val token = status.token ?: error("O token de pareamento não foi entregue.")
                    return DeviceSession(status.device?.id.orEmpty(), token).also { session ->
                        tokens.save(session)
                        http.bindServer(base)
                    }
                }
                "rejected" -> error("Pareamento recusado no computador.")
                "expired" -> error("O código expirou. Gere outro.")
            }
        }
    }

    fun isPaired() = tokens.hasToken()
    suspend fun profiles(): List<com.brasa.tv.core.model.Profile> {
        val cached = profileCache
        if (cached != null && System.currentTimeMillis() - cached.first < PROFILE_CACHE_MS) return cached.second
        return api.profiles(requireServer()).also { profileCache = System.currentTimeMillis() to it }
    }
    suspend fun selectProfile(id: String) = settings.saveProfile(id)

    suspend fun cachedHome(profileId: String): HomeResponse? {
        val base = requireServer()
        return cache.home(base, profileId)
    }

    suspend fun home(profileId: String): HomeResponse {
        val base = requireServer()
        val response = runCatching { api.home(base, profileId) }.getOrElse { catalogAsHome(api.catalog(base, profileId)) }
        val resolved = response.copy(rows = response.rows.map { row -> row.copy(items = row.items.map { it.withArtwork(base) }) })
        cache.saveHome(base, profileId, resolved)
        return resolved
    }

    suspend fun catalog(profileId: String): CatalogResponse {
        val base = requireServer()
        val value = api.catalog(base, profileId)
        return value.copy(movies = value.movies.map { it.withArtwork(base) }, series = value.series.map { it.withArtwork(base) })
    }

    suspend fun playback(profileId: String, key: String, forceRefresh: Boolean = false): PlaybackInfo {
        val cacheKey = "$profileId:$key"
        val cached = playbackCache[cacheKey]
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.first < PLAYBACK_CACHE_MS) return cached.second
        val value = api.playback(requireServer(), profileId, key)
        if (value.preparationStatus == "ready") playbackCache[cacheKey] = System.currentTimeMillis() to value else playbackCache.remove(cacheKey)
        return value
    }

    suspend fun prefetchPlayback(profileId: String, key: String) { runCatching { playback(profileId, key) } }
    suspend fun saveProgress(profileId: String, key: String, value: WatchProgress) = api.progress(requireServer(), profileId, key, value)
    suspend fun favorite(profileId: String, key: String, enabled: Boolean) = api.favorite(requireServer(), profileId, key, enabled)
    suspend fun verifyPin(profileId: String, pin: String) = api.verifyPin(requireServer(), profileId, pin).valid
    suspend fun networkStatus() = api.networkStatus(requireServer())
    suspend fun startNetworkTest(profile: String, durationSeconds: Int = 60) = api.startNetworkTest(requireServer(), profile, durationSeconds)
    suspend fun networkTestStatus(id: String) = api.networkTestStatus(requireServer(), id)
    suspend fun measureNetworkTest(path: String, onProgress: (Long, Long) -> Unit) = api.measureNetworkTest(requireServer(), path, onProgress)
    suspend fun cancelNetworkTest(id: String) = api.cancelNetworkTest(requireServer(), id)

    suspend fun forget() {
        tokens.clear()
        settings.forgetServer()
        playbackCache.clear()
        profileCache = null
        cache.clear()
    }

    suspend fun search(profileId: String, query: String): List<CatalogItem> {
        val value = query.trim()
        if (value.isBlank()) return emptyList()
        val base = requireServer()
        return runCatching { api.search(base, profileId, value).map { it.withArtwork(base) } }.getOrElse {
            val catalog = catalog(profileId)
            (catalog.movies + catalog.series + catalog.series.flatMap { it.seasons.flatMap { season -> season.episodes } }).filter { item ->
                listOf(item.title, item.originalTitle, item.overview, item.genres.joinToString()).any { it.contains(value, ignoreCase = true) }
            }
        }
    }

    private suspend fun requireServer() = settings().serverBaseUrl.ifBlank { error("Nenhum servidor configurado.") }
    private fun CatalogItem.withArtwork(base: String): CatalogItem = copy(
        poster = poster.toLocalUrl(base),
        backdrop = backdrop.toLocalUrl(base),
        seasons = seasons.map { season -> season.copy(episodes = season.episodes.map { it.withArtwork(base) }) },
    )
    private fun String.toLocalUrl(base: String) = if (isBlank()) "" else LocalServerAddress.resolve(base, this)

    private fun catalogAsHome(catalog: CatalogResponse): HomeResponse {
        val allEpisodes = catalog.series.flatMap { it.seasons.flatMap(Season::episodes) }
        val continuing = (catalog.movies + allEpisodes).filter { (it.progress?.percentage ?: 0.0) in 0.1..94.9 }
        val favorites = catalog.movies.filter(CatalogItem::favorite)
        return HomeResponse(
            catalog.profile,
            listOf(
                HomeRow("continue", "Continuar assistindo", items = continuing),
                HomeRow("movies", "Filmes", items = catalog.movies),
                HomeRow("series", "Séries", items = catalog.series),
                HomeRow("favorites", "Minha lista", items = favorites),
            ).filter { it.items.isNotEmpty() },
        )
    }

    private companion object { const val PLAYBACK_CACHE_MS = 60_000L; const val PROFILE_CACHE_MS = 30_000L }
}

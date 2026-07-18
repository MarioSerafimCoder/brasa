package com.brasa.tv.feature.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdateRepositoryTest {
    private class Cache : UpdateCheckCache {
        override var lastCheckAt = 0L
        override var deferredVersionCode = 0L
        override var cachedResponse: UpdateCheckResponse? = null
    }

    @Test
    fun respectsAutomaticWindowAndManualCheck() = runBlocking {
        val cache = Cache()
        var calls = 0
        var now = 1_000_000L
        val update = UpdateManifest(packageName = "com.brasa.tv", versionCode = 2, versionName = "1.0.1")
        val api = object : UpdateApi {
            override suspend fun check(baseUrl: String, client: UpdateClientInfo): UpdateCheckResponse {
                calls++
                return UpdateCheckResponse(true, update)
            }
        }
        val repository = UpdateRepository(api, cache, { "http://127.0.0.1:4173" }, { now }, UpdateClientInfo(1, "1.0.0"))
        assertTrue(repository.check().available)
        assertEquals(1, calls)
        assertTrue(repository.check().available)
        assertEquals(1, calls)
        assertTrue(repository.check(true).available)
        assertEquals(2, calls)
        repository.defer(2)
        assertFalse(repository.check().available)
        now += 24 * 60 * 60 * 1000 + 1
        assertFalse(repository.check().available)
        assertEquals(3, calls)
        assertTrue(repository.check(true).available)
    }

    @Test
    fun propagatesNetworkFailure() = runBlocking {
        val repository = UpdateRepository(
            object : UpdateApi {
                override suspend fun check(baseUrl: String, client: UpdateClientInfo): UpdateCheckResponse = error("offline")
            },
            Cache(),
            { "http://127.0.0.1:4173" },
            client = UpdateClientInfo(1, "1.0.0"),
        )
        val result = runCatching { repository.check(true) }
        assertTrue(result.isFailure)
        assertEquals("offline", result.exceptionOrNull()?.message)
    }

    @Test
    fun neverOffersInstalledOrOlderVersionFromCacheOrServer() = runBlocking {
        val cache = Cache().apply {
            lastCheckAt = 999_000
            cachedResponse = UpdateCheckResponse(true, UpdateManifest(versionCode = 12, versionName = "1.0.11"))
        }
        var response = cache.cachedResponse!!
        val api = object : UpdateApi {
            override suspend fun check(baseUrl: String, client: UpdateClientInfo) = response
        }
        val repository = UpdateRepository(api, cache, { "http://127.0.0.1:4173" }, { 1_000_000 }, UpdateClientInfo(12, "1.0.11"))

        assertFalse(repository.check().available)
        assertFalse(repository.check(true).available)
        response = UpdateCheckResponse(true, UpdateManifest(versionCode = 11, versionName = "1.0.10"))
        assertFalse(repository.check(true).available)
        response = UpdateCheckResponse(true, UpdateManifest(versionCode = 13, versionName = "1.0.12"))
        assertTrue(repository.check(true).available)
    }

    @Test
    fun installationClearsStaleUpdateState() {
        val cache = Cache().apply {
            lastCheckAt = 123
            deferredVersionCode = 12
            cachedResponse = UpdateCheckResponse(true, UpdateManifest(versionCode = 12))
        }
        val repository = UpdateRepository(
            object : UpdateApi {
                override suspend fun check(baseUrl: String, client: UpdateClientInfo) = UpdateCheckResponse()
            },
            cache,
            { "http://127.0.0.1:4173" },
            client = UpdateClientInfo(12, "1.0.11"),
        )

        repository.markInstalled()

        assertEquals(0, cache.lastCheckAt)
        assertEquals(0, cache.deferredVersionCode)
        assertNull(cache.cachedResponse)
    }
}

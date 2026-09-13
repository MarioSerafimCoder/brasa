@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.core.playback

import android.app.Application
import com.brasa.tv.data.storage.AppSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaybackExtrasAndroidTest {
    @Test fun nextEpisodeRequestHasHardByteLimitAndUsesPlaybackCacheKey() {
        val spec = nextEpisodeDataSpec("http://192.168.1.10:4173", "/api/tv/stream/episode:2", "revision-key")
        assertEquals(512L * 1024, spec.length)
        assertEquals(0L, spec.position)
        assertEquals("revision-key", spec.key)
        assertEquals("http://192.168.1.10:4173/api/tv/stream/episode:2", spec.uri.toString())
    }
    @Test fun stabilityPreferencePersistsAndIsIsolatedByProfile() = runBlocking {
        val store = AppSettingsStore(RuntimeEnvironment.getApplication())
        store.saveProfile("extras-adult"); store.saveStability("extras-adult", true)
        assertTrue(store.values.first().prioritizeStability)
        store.saveProfile("extras-kids"); assertFalse(store.values.first().prioritizeStability)
        store.saveProfile("extras-adult"); assertTrue(store.values.first().prioritizeStability)
        store.saveStability("extras-adult", false); assertFalse(store.values.first().prioritizeStability)
    }
}

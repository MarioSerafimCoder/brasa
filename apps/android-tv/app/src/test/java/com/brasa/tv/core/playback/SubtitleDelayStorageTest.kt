package com.brasa.tv.core.playback

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.brasa.tv.data.storage.AppSettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SubtitleDelayStorageTest {
    @Test fun offsetSurvivesReopeningAndIsScopedToServerProfileAndMedia() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val store=AppSettingsStore(context)
        store.saveSubtitleDelay("http://server:4173","p1","episode:1",750)
        val reopened=AppSettingsStore(context)
        assertEquals(750L,reopened.subtitleDelay("http://server:4173","p1","episode:1"))
        assertEquals(0L,reopened.subtitleDelay("http://server:4173","p2","episode:1"))
        assertEquals(0L,reopened.subtitleDelay("http://other:4173","p1","episode:1"))
        assertEquals(0L,reopened.subtitleDelay("http://server:4173","p1","episode:2"))
        store.saveSubtitleDelay("http://server:4173","p1","episode:1",-250)
        assertEquals(-250L,reopened.subtitleDelay("http://server:4173","p1","episode:1"))
        store.saveSubtitleDelay("http://server:4173","p1","episode:1",0)
        assertEquals(0L,reopened.subtitleDelay("http://server:4173","p1","episode:1"))
    }
}

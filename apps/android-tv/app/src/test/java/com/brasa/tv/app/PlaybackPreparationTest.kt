package com.brasa.tv.app

import com.brasa.tv.core.model.PlaybackInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class PlaybackPreparationTest {
    @Test
    fun stopsAtReadyAndPreservesAbsolutePosition() = runBlocking {
        val published = mutableListOf<PlaybackInfo>()
        var attempts = 0
        val finished =
            awaitPlaybackPreparation(
                false,
                load = {
                    attempts++
                    PlaybackInfo(
                        mediaKey = "episode:a",
                        playbackOffset = 120_000,
                        resumePosition = 5_000,
                        preparationStatus = if (attempts == 2) "ready" else "preparing",
                    )
                },
                publish = published::add,
                intervalMs = 0,
            )
        assertTrue(finished)
        assertEquals(2, attempts)
        assertEquals(125_000L, published.last().playbackOffset + published.last().resumePosition)
    }

    @Test
    fun boundedPollingTimesOutAndServerFailureStopsImmediately() = runBlocking {
        var attempts = 0
        assertFalse(
            awaitPlaybackPreparation(
                false,
                load = {
                    attempts++
                    PlaybackInfo(preparationStatus = "preparing")
                },
                publish = {},
                maxPolls = 3,
                intervalMs = 0,
            )
        )
        assertEquals(3, attempts)
        assertTrue(
            awaitPlaybackPreparation(
                false,
                load = { PlaybackInfo(preparationStatus = "failed") },
                publish = {},
                intervalMs = 0,
            )
        )
    }

    @Test
    fun cancelledRequestCannotPublishLateNetworkResponse() = runBlocking {
        val response = CompletableDeferred<PlaybackInfo>()
        val published = mutableListOf<PlaybackInfo>()
        val old =
            launch(start = CoroutineStart.UNDISPATCHED) {
                awaitPlaybackPreparation(
                    false,
                    load = { withContext(NonCancellable) { response.await() } },
                    publish = published::add,
                    intervalMs = 0,
                )
            }
        old.cancel()
        awaitPlaybackPreparation(
            false,
            load = { PlaybackInfo(mediaKey = "movie:new") },
            publish = published::add,
            intervalMs = 0,
        )
        response.complete(PlaybackInfo(mediaKey = "movie:old"))
        old.join()
        assertEquals(listOf("movie:new"), published.map { it.mediaKey })
    }
}

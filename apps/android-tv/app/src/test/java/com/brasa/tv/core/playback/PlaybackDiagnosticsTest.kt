package com.brasa.tv.core.playback

import com.brasa.tv.core.model.PlaybackInfo
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackDiagnosticsTest {
    @Test fun adaptiveRecoveryRequiresTwoSubstantialRecentInterruptions() {
        val policy = AdaptiveFallbackPolicy()
        assertFalse(policy.onInterruption(10_000, 200, true))
        assertFalse(policy.onInterruption(20_000, 2_000, false))
        assertFalse(policy.onInterruption(30_000, 2_000, true))
        assertTrue(policy.onInterruption(80_000, 2_000, true))
        assertFalse(policy.onInterruption(90_000, 3_000, true))
    }
    @Test fun oldInterruptionsDoNotTriggerConversion() {
        val policy = AdaptiveFallbackPolicy()
        assertFalse(policy.onInterruption(10_000, 2_000, true))
        assertFalse(policy.onInterruption(140_001, 2_000, true))
    }
    @Test fun oneSessionSpansPreparationAndQualityChangesAndClosesOutstandingEvents() = runBlocking {
        val received = mutableListOf<PlaybackBatch>()
        val delivered = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var now = 0L
        val recorder = PlaybackDiagnosticsRecorder("movie:test", scope, { now }) { batch ->
            synchronized(received) { received.add(batch) }
            if (batch.events.any { it.kind == "end" }) delivered.complete(Unit)
        }
        try {
            recorder.source(PlaybackInfo(mediaKey = "movie:test", playbackMode = "direct"))
            now = 2_000
            recorder.conversion("network")
            recorder.source(PlaybackInfo(mediaKey = "movie:test", playbackMode = "hls", hlsSessionId = "a".repeat(24), preparationStatus = "preparing"))
            recorder.source(PlaybackInfo(mediaKey = "movie:test", playbackMode = "hls", hlsSessionId = "a".repeat(24), preparationStatus = "ready"))
            recorder.beforeFinish = { recorder.record(PlaybackEvent(kind = "buffer_end", durationMs = 1_500)) }
            recorder.finish()
            withTimeout(8_000) { delivered.await() }
            val events = synchronized(received) { received.flatMap { it.events } }
            assertEquals(1, received.map { it.id }.distinct().size)
            assertEquals(1, events.count { it.kind == "conversion" })
            assertEquals("buffer_end", events[events.lastIndex - 1].kind)
            assertEquals("end", events.last().kind)
            assertEquals("a".repeat(24), events.last().hlsSessionId)
            assertEquals(events.map { it.sequence }.sorted(), events.map { it.sequence })
        } finally { scope.cancel() }
    }
    @Test fun queueIsBoundedAndPreservesRecentErrors() = runBlocking {
        val delivered = CompletableDeferred<List<PlaybackEvent>>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val all = mutableListOf<PlaybackEvent>()
        val recorder = PlaybackDiagnosticsRecorder("movie:test", scope, { 0L }) { batch ->
            all.addAll(batch.events)
            if (batch.events.any { it.kind == "end" }) delivered.complete(all.toList())
        }
        try {
            recorder.record(PlaybackEvent(kind = "error", errorCode = 2002))
            repeat(1000) { recorder.record(PlaybackEvent(kind = "sample")) }
            recorder.finish()
            val events = withTimeout(8_000) { delivered.await() }
            assertTrue(events.size <= 240)
            assertTrue(events.any { it.errorCode == 2002 })
        } finally { scope.cancel() }
    }
}

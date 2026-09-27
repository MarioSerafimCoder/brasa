package com.brasa.tv.app

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OperationTrackerTest {
    @Test
    fun cacheFinishingDoesNotFinishPlaybackOrClearItsError() = runBlocking {
        var state = OperationStates()
        val tracker = OperationTracker { state = it }
        val playback = CompletableDeferred<Unit>()
        val cache = CompletableDeferred<Unit>()
        val first =
            launch(start = CoroutineStart.UNDISPATCHED) {
                tracker.run(OperationDomain.Playback, { throw it }) { playback.await() }
            }
        val second =
            launch(start = CoroutineStart.UNDISPATCHED) {
                tracker.run(OperationDomain.Cache, { throw it }) { cache.await() }
            }
        tracker.message(OperationDomain.Playback, "Preparando")
        cache.complete(Unit)
        second.join()
        assertFalse(state.cache.loading)
        assertTrue(state.playback.loading)
        assertEquals("Preparando", state.playback.message)
        first.cancelAndJoin()
        assertFalse(state.playback.loading)
    }

    @Test
    fun overlappingRequestsAndOldProfileCompletionCannotClearNewOperation() = runBlocking {
        var state = OperationStates()
        val tracker = OperationTracker { state = it }
        val release = CompletableDeferred<Unit>()
        val old =
            launch(start = CoroutineStart.UNDISPATCHED) {
                tracker.run(OperationDomain.Home, { throw it }) { release.await() }
            }
        val other =
            launch(start = CoroutineStart.UNDISPATCHED) {
                tracker.run(OperationDomain.Home, { throw it }) { awaitCancellation() }
            }
        assertEquals(2, state.home.pending)
        other.cancelAndJoin()
        assertEquals(1, state.home.pending)
        tracker.reset()
        val current =
            launch(start = CoroutineStart.UNDISPATCHED) {
                tracker.run(OperationDomain.Home, { throw it }) { awaitCancellation() }
            }
        release.complete(Unit)
        old.join()
        assertEquals(1, state.home.pending)
        current.cancelAndJoin()
        assertFalse(state.home.loading)
    }

    @Test
    fun failureAndCancellationAlwaysReleaseTheirOwnOperation() = runBlocking {
        var state = OperationStates()
        val tracker = OperationTracker { state = it }
        tracker.run(OperationDomain.Cache, { tracker.message(OperationDomain.Cache, "Falhou") }) {
            error("disk")
        }
        assertEquals("Falhou", state.cache.message)
        assertFalse(state.cache.loading)
        var reported = false
        val job =
            launch(start = CoroutineStart.UNDISPATCHED) {
                tracker.run(OperationDomain.Playback, { reported = true }) { awaitCancellation() }
            }
        job.cancelAndJoin()
        assertFalse(reported)
        assertFalse(state.playback.loading)
    }
}

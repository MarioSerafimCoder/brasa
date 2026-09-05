package com.brasa.tv.data.repository

import com.brasa.tv.core.model.LibraryScanStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryScannerTest {
    @Test fun waitsForCompletionAndPublishesProgress() = runBlocking {
        val states = mutableListOf<String>()
        var polls = 0
        val result = awaitLibraryScan(
            start = { LibraryScanStatus("scan", "syncing") },
            status = { polls++; LibraryScanStatus("scan", if (polls == 1) "syncing" else "complete") },
            onStatus = { states += it.state }, pollIntervalMs = 1,
        )
        assertEquals("complete", result.state)
        assertEquals(listOf("syncing", "syncing", "complete"), states)
    }

    @Test fun reportsServerFailure() = runBlocking {
        val failure = runCatching {
            awaitLibraryScan({ LibraryScanStatus("scan", "error", "Falhou") }, { error("must not poll") }, {})
        }.exceptionOrNull()
        assertEquals("Falhou", failure?.message)
    }

    @Test fun rejectsChangedOperationAfterServerRestart() = runBlocking {
        val failure = runCatching {
            awaitLibraryScan({ LibraryScanStatus("old", "syncing") }, { LibraryScanStatus("new", "complete") }, {}, pollIntervalMs = 1)
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("reiniciou"))
    }

    @Test fun boundsWaitAndAllowsCallerToRetry() = runBlocking {
        val failure = runCatching {
            awaitLibraryScan({ LibraryScanStatus("scan", "syncing") }, { delay(1_000); LibraryScanStatus() }, {}, pollIntervalMs = 1, timeoutMs = 30)
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("demorando"))
        val retry = awaitLibraryScan({ LibraryScanStatus("retry", "complete") }, { error("must not poll") }, {})
        assertEquals("complete", retry.state)
    }

    @Test fun propagatesNetworkFailureAndCancellation() = runBlocking {
        val failure = runCatching {
            awaitLibraryScan({ throw java.io.IOException("offline") }, { LibraryScanStatus() }, {})
        }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        val cancellation = runCatching {
            awaitLibraryScan({ throw CancellationException("cancelled") }, { LibraryScanStatus() }, {})
        }.exceptionOrNull()
        assertTrue(cancellation is CancellationException)
    }
}

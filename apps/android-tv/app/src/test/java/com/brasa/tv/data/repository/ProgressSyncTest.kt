package com.brasa.tv.data.repository

import android.app.Application
import com.brasa.tv.core.model.WatchProgress
import com.brasa.tv.data.storage.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [PosixAtomicFileShadow::class])
class ProgressSyncTest {
    @get:Rule val temp = TemporaryFolder()
    private val target = ProgressDestination("http://192.168.1.10:4173", "tv1")
    @Test fun offlineProgressSurvivesRestartAndLatestPositionWins() = runBlocking {
        val file = temp.root.resolve("outbox.json")
        val store = ProgressOutbox(file, Json)
        // Cancel the automatic pump; exercise the exact same flush operation deterministically.
        val stopped = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.Default)
        val sync = ProgressSync(store, { throw IOException("offline") }, stopped)
        sync.bind(target)
        sync.save("adult", "movie:1", WatchProgress(currentTime = 100.0))
        sync.save("adult", "movie:1", WatchProgress(currentTime = 130.0))
        sync.save("kids", "movie:1", WatchProgress(currentTime = 40.0))
        sync.flush()
        val restored = ProgressOutbox(file, Json)
        assertEquals(2, restored.pending(target).size)
        assertEquals(130.0, restored.pending(target).first { it.profileId == "adult" }.value.currentTime, 0.0)
        val sent = mutableListOf<PendingProgress>()
        val online = ProgressSync(restored, { sent.add(it) }, stopped)
        online.bind(target); online.flush()
        assertEquals(2, sent.size); assertTrue(restored.pending(target).isEmpty())
        assertTrue(sent.all { it.value.updatedAt.endsWith("Z") })
    }
    @Test fun acknowledgementCannotDeleteProgressSavedWhileRequestWasInFlight() = runBlocking {
        val store = ProgressOutbox(temp.root.resolve("outbox.json"), Json)
        val first = PendingProgress(destination = target, profileId = "adult", mediaKey = "movie:1", value = WatchProgress(currentTime = 100.0))
        store.put(first)
        store.put(first.copy(id = "newer", value = WatchProgress(currentTime = 140.0)))
        store.acknowledge(first.id)
        assertEquals(140.0, store.pending(target).single().value.currentTime, 0.0)
    }
    @Test fun serverAndDeviceChangesCannotReceiveAnotherPairingsProgress() = runBlocking {
        val store = ProgressOutbox(temp.root.resolve("outbox.json"), Json)
        store.put(PendingProgress(destination = target, profileId = "adult", mediaKey = "movie:1", value = WatchProgress(currentTime = 100.0)))
        val stopped = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.Default)
        val sent = mutableListOf<PendingProgress>()
        val sync = ProgressSync(store, { sent.add(it) }, stopped)
        sync.bind(target.copy(deviceId = "new-pairing")); sync.flush()
        sync.bind(target.copy(server = "http://192.168.1.20:4173")); sync.flush()
        assertTrue(sent.isEmpty()); assertEquals(1, store.pending(target).size)
        sync.bind(target); sync.forget()
        assertTrue(store.pending(target).isEmpty())
    }
    @Test fun malformedLocalJournalIsNotSilentlyOverwritten() = runBlocking {
        val file = temp.root.resolve("outbox.json"); file.writeText("broken journal")
        val store = ProgressOutbox(file, Json)
        try { store.put(PendingProgress(destination = target, profileId = "adult", mediaKey = "movie:1", value = WatchProgress())); fail("must retain unreadable journal") }
        catch (_: kotlinx.serialization.SerializationException) { assertEquals("broken journal", file.readText()) }
    }
    @Test fun automaticPumpRetriesWhenAppReturnsOnline() = runBlocking {
        val store = ProgressOutbox(temp.root.resolve("outbox.json"), Json)
        val online = AtomicBoolean(false)
        val sent = AtomicBoolean(false)
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sync = ProgressSync(store, { if (!online.get()) throw IOException("offline"); sent.set(true) }, background)
            sync.bind(target)
            sync.enqueue("adult", "movie:1", WatchProgress(currentTime = 150.0)).join()
            withTimeout(5_000) { while (sync.state.value.pending != 1) delay(20) }
            assertFalse(sent.get())
            online.set(true); sync.retry()
            withTimeout(5_000) { while (!sent.get() || store.pending(target).isNotEmpty()) delay(20) }
        } finally { background.cancel() }
    }
    @Test fun storageFailureRemainsVisibleAfterNetworkRetry() = runBlocking {
        val file = temp.root.resolve("outbox.json"); file.writeText("broken journal")
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sync = ProgressSync(ProgressOutbox(file, Json), { fail("cannot send unreadable journal") }, background)
            sync.bind(target)
            sync.enqueue("adult", "movie:1", WatchProgress(currentTime = 150.0)).join()
            sync.flush()
            assertTrue(sync.state.value.message.startsWith("Não foi possível guardar"))
            assertEquals("broken journal", file.readText())
        } finally { background.cancel() }
    }
    @Test fun interruptedWriteKeepsLastCommittedPosition() = runBlocking {
        val file = temp.root.resolve("outbox.json")
        val store = ProgressOutbox(file, Json)
        store.put(PendingProgress(destination = target, profileId = "adult", mediaKey = "movie:1", value = WatchProgress(currentTime = 100.0)))
        // Simulate process death after writing only part of the next journal.
        android.util.AtomicFile(file).startWrite().use { it.write("partial update".toByteArray()) }
        assertEquals(100.0, ProgressOutbox(file, Json).pending(target).single().value.currentTime, 0.0)
    }
}

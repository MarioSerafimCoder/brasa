package com.brasa.tv.data.storage

import android.util.AtomicFile
import com.brasa.tv.core.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

@Serializable data class ProgressDestination(val server: String, val deviceId: String)
@Serializable data class PendingProgress(
    val id: String = UUID.randomUUID().toString(), val destination: ProgressDestination,
    val profileId: String, val mediaKey: String, val value: WatchProgress,
)

/** AtomicFile keeps the last committed journal if a write or process is interrupted. */
class ProgressOutbox(file: File, private val json: Json) {
    private val journal = AtomicFile(file)
    private val mutex = Mutex()
    private val serializer = ListSerializer(PendingProgress.serializer())
    private fun read(): List<PendingProgress> = if (!journal.baseFile.exists() && !File(journal.baseFile.path + ".bak").exists()) emptyList()
        else journal.openRead().bufferedReader().use { json.decodeFromString(serializer, it.readText()) }
    private fun write(entries: List<PendingProgress>) {
        val bytes = json.encodeToString(serializer, entries).toByteArray(Charsets.UTF_8)
        val stream = journal.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            journal.finishWrite(stream)
            // AtomicFile logs some commit failures instead of throwing. Never acknowledge one.
            if (!journal.readFully().contentEquals(bytes)) throw IOException("Progress journal commit failed")
        }
        catch (error: Exception) { journal.failWrite(stream); throw error }
    }
    suspend fun put(entry: PendingProgress) = withContext(Dispatchers.IO) { mutex.withLock {
        val entries = read().filterNot { it.destination == entry.destination && it.profileId == entry.profileId && it.mediaKey == entry.mediaKey }
        write(entries + entry)
    } }
    suspend fun pending(destination: ProgressDestination): List<PendingProgress> = withContext(Dispatchers.IO) { mutex.withLock { read().filter { it.destination == destination } } }
    suspend fun acknowledge(id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val entries = read(); val remaining = entries.filterNot { it.id == id }
        if (remaining.size != entries.size) write(remaining)
    } }
    suspend fun clear(destination: ProgressDestination) = withContext(Dispatchers.IO) { mutex.withLock { write(read().filterNot { it.destination == destination }) } }
}

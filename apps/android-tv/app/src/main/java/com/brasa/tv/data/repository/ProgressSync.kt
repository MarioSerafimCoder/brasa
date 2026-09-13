package com.brasa.tv.data.repository

import com.brasa.tv.core.model.WatchProgress
import com.brasa.tv.core.network.BrasaApiException
import com.brasa.tv.core.network.DeviceRevokedException
import com.brasa.tv.data.storage.PendingProgress
import com.brasa.tv.data.storage.ProgressDestination
import com.brasa.tv.data.storage.ProgressOutbox
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ProgressSyncState(val pending: Int = 0, val message: String = "Progresso sincronizado.")

class ProgressSync(
    private val outbox: ProgressOutbox,
    private val send: suspend (PendingProgress) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    @Volatile private var destination: ProgressDestination? = null
    @Volatile private var localWriteFailed = false
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val drain = Mutex()
    private val writes = Mutex()
    private val mutable = MutableStateFlow(ProgressSyncState())
    val state = mutable.asStateFlow()
    init { scope.launch {
        while (isActive) {
            withTimeoutOrNull(30_000) { wake.receive() }
            flush()
        }
    } }
    fun bind(value: ProgressDestination) { destination = value; wake.trySend(Unit) }
    fun retry() { wake.trySend(Unit) }
    /** Capture the destination before launching: changing profiles/server cannot redirect a save. */
    fun enqueue(profileId: String, mediaKey: String, value: WatchProgress): Job {
        val target = destination
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { checkNotNull(target) { "Servidor não pareado." }; persist(target, profileId, mediaKey, value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                localWriteFailed = true
                mutable.value = ProgressSyncState(mutable.value.pending, "Não foi possível guardar o progresso na TV. Verifique o espaço livre.")
            }
        }
    }
    suspend fun save(profileId: String, mediaKey: String, value: WatchProgress): WatchProgress {
        persist(checkNotNull(destination) { "Servidor não pareado." }, profileId, mediaKey, value)
        return value
    }
    private suspend fun persist(target: ProgressDestination, profileId: String, mediaKey: String, value: WatchProgress) = writes.withLock {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        outbox.put(PendingProgress(destination = target, profileId = profileId, mediaKey = mediaKey, value = value.copy(updatedAt = timestamp)))
        localWriteFailed = false
        if (destination == target) {
            val count = outbox.pending(target).size
            mutable.value = ProgressSyncState(count, "Ponto salvo na TV. Aguardando sincronização com o computador.")
        }
        wake.trySend(Unit)
    }
    suspend fun pending(profileId: String, mediaKey: String): WatchProgress? = destination?.let { target ->
        outbox.pending(target).find { it.profileId == profileId && it.mediaKey == mediaKey }?.value
    }
    suspend fun flush(profileId: String? = null, mediaKey: String? = null) = drain.withLock {
        val target = destination ?: return@withLock
        var journalLoaded = false
        try {
            val pending = outbox.pending(target).filter { (profileId == null || it.profileId == profileId) && (mediaKey == null || it.mediaKey == mediaKey) }
            journalLoaded = true
            var blocked = false
            for (entry in pending) {
                if (destination != target) return@withLock
                try { send(entry); outbox.acknowledge(entry.id) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: DeviceRevokedException) {
                    mutable.value = ProgressSyncState(pending.size, "Progresso guardado na TV. Reconecte o dispositivo para sincronizar.")
                    return@withLock
                }
                catch (error: BrasaApiException) {
                    if (error.status in listOf(400, 403, 404, 410, 422)) { blocked = true; continue }
                    throw error
                }
            }
            if (destination != target || localWriteFailed) return@withLock
            val remaining = outbox.pending(target).size
            mutable.value = ProgressSyncState(remaining, when {
                blocked -> "Há progresso guardado na TV para um perfil ou título indisponível no computador."
                remaining > 0 -> "Ponto salvo na TV. Aguardando sincronização."
                else -> "Progresso sincronizado."
            })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (destination == target && !localWriteFailed) mutable.value = ProgressSyncState(mutable.value.pending,
                if (journalLoaded) "Progresso guardado na TV. Será reenviado quando a conexão voltar."
                else "Não foi possível ler o progresso guardado na TV. Verifique o armazenamento do aparelho.")
        }
    }
    suspend fun forget() {
        val previous = destination
        destination = null
        drain.withLock { writes.withLock { if (previous != null) outbox.clear(previous) } }
        localWriteFailed = false
        mutable.value = ProgressSyncState()
    }
}

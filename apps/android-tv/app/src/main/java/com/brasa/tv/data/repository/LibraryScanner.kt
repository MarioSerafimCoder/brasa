package com.brasa.tv.data.repository

import com.brasa.tv.core.model.LibraryScanStatus
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

internal suspend fun awaitLibraryScan(
    start: suspend () -> LibraryScanStatus,
    status: suspend () -> LibraryScanStatus,
    onStatus: (LibraryScanStatus) -> Unit,
    pollIntervalMs: Long = 1_500,
    timeoutMs: Long = 600_000,
): LibraryScanStatus {
    try {
        return withTimeout(timeoutMs) {
            var current = start()
            val scanId = current.id
            check(scanId.isNotBlank()) { "O servidor não confirmou a busca." }
            while (true) {
                check(current.id == scanId) { "O servidor reiniciou ou iniciou outra busca. Tente novamente." }
                onStatus(current)
                when (current.state) {
                    "complete" -> return@withTimeout current
                    "error" -> error(current.message.ifBlank { "A busca falhou. Tente novamente." })
                    "syncing" -> Unit
                    else -> error("O servidor não confirmou o andamento da busca. Tente novamente.")
                }
                delay(pollIntervalMs)
                current = status()
            }
            @Suppress("UNREACHABLE_CODE")
            current
        }
    } catch (_: TimeoutCancellationException) {
        error("A busca está demorando mais que o esperado. Ela pode continuar no computador; tente consultar novamente depois.")
    }
}

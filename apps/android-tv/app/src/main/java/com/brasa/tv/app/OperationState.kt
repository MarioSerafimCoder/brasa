package com.brasa.tv.app

import kotlinx.coroutines.CancellationException

enum class OperationDomain {
    Session,
    Home,
    Catalog,
    Playback,
    Cache,
    Personalization,
}

data class OperationStatus(val pending: Int = 0, val message: String = "") {
    val loading: Boolean
        get() = pending > 0
}

data class OperationStates(
    val session: OperationStatus = OperationStatus(),
    val home: OperationStatus = OperationStatus(),
    val catalog: OperationStatus = OperationStatus(),
    val playback: OperationStatus = OperationStatus(),
    val cache: OperationStatus = OperationStatus(),
    val personalization: OperationStatus = OperationStatus(),
) {
    operator fun get(domain: OperationDomain): OperationStatus =
        when (domain) {
            OperationDomain.Session -> session
            OperationDomain.Home -> home
            OperationDomain.Catalog -> catalog
            OperationDomain.Playback -> playback
            OperationDomain.Cache -> cache
            OperationDomain.Personalization -> personalization
        }

    fun with(domain: OperationDomain, status: OperationStatus): OperationStates =
        when (domain) {
            OperationDomain.Session -> copy(session = status)
            OperationDomain.Home -> copy(home = status)
            OperationDomain.Catalog -> copy(catalog = status)
            OperationDomain.Playback -> copy(playback = status)
            OperationDomain.Cache -> copy(cache = status)
            OperationDomain.Personalization -> copy(personalization = status)
        }
}

/** Main-thread operation ownership; ending one request cannot finish another. */
internal class OperationTracker(private val publish: (OperationStates) -> Unit) {
    private var states = OperationStates()
    private var generation = 0L
    private var sequence = 0L
    private val latest = mutableMapOf<OperationDomain, Long>()

    fun message(domain: OperationDomain, message: String) {
        states = states.with(domain, states[domain].copy(message = message))
        publish(states)
    }

    fun reset() {
        generation++
        latest.clear()
        states = OperationStates()
        publish(states)
    }

    suspend fun run(
        domain: OperationDomain,
        onError: (Throwable) -> Unit,
        block: suspend () -> Unit,
    ) {
        val startedGeneration = generation
        val id = ++sequence
        latest[domain] = id
        states =
            states.with(
                domain,
                states[domain].copy(pending = states[domain].pending + 1, message = ""),
            )
        publish(states)
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (generation == startedGeneration && latest[domain] == id) onError(failure)
        } finally {
            if (generation == startedGeneration) {
                states =
                    states.with(
                        domain,
                        states[domain].copy(pending = (states[domain].pending - 1).coerceAtLeast(0)),
                    )
                publish(states)
            }
        }
    }
}

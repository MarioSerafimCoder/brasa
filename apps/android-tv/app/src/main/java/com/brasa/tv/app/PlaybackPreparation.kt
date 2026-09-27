package com.brasa.tv.app

import com.brasa.tv.core.model.PlaybackInfo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Poll the same source/position; cancellation must win even over a late network response. */
internal suspend fun awaitPlaybackPreparation(
    waitBeforeFirst: Boolean,
    load: suspend () -> PlaybackInfo,
    publish: (PlaybackInfo) -> Unit,
    maxPolls: Int = 600,
    intervalMs: Long = 1_000,
): Boolean {
    if (waitBeforeFirst) delay(intervalMs)
    repeat(maxPolls) {
        currentCoroutineContext().ensureActive()
        val next = load()
        currentCoroutineContext().ensureActive()
        publish(next)
        if (next.preparationStatus in setOf("ready", "failed")) return true
        delay(intervalMs)
    }
    return false
}

package com.brasa.tv.core.playback

/** Uses elapsed time and position, not isPlaying: a stalled decoder can report playing. */
class PlaybackRecovery {
    private var lastPosition: Long? = null
    private var stalledSince: Long? = null
    private var stableSince: Long? = null
    private var lastPrematureEnd: Long? = null
    var attempts: Int = 0
        private set

    fun sample(nowMs: Long, positionMs: Long, expectedToAdvance: Boolean): Boolean {
        val previous = lastPosition
        lastPosition = positionMs
        if (!expectedToAdvance || previous == null || positionMs < previous) {
            stalledSince = null
            stableSince = null
            return false
        }
        if (positionMs > previous + 250) {
            stalledSince = null
            if (stableSince == null) stableSince = nowMs
            if (nowMs - stableSince!! >= 60_000) attempts = 0
            return false
        }
        stableSince = null
        if (stalledSince == null) stalledSince = nowMs
        return nowMs - stalledSince!! >= 12_000
    }

    fun beginRetry(): Boolean {
        if (attempts >= 2) return false
        attempts++
        resetSampling()
        return true
    }

    fun resetSampling() { lastPosition = null; stalledSince = null; stableSince = null }

    fun canRecoverPrematureEnd(positionMs: Long): Boolean {
        val previous = lastPrematureEnd
        lastPrematureEnd = positionMs
        return previous == null || positionMs > previous + 30_000
    }

    companion object {
        fun isPrematureEnd(positionMs: Long, durationMs: Long): Boolean =
            durationMs > 0 && positionMs < durationMs - 30_000 && positionMs.toDouble() / durationMs < .98
    }
}

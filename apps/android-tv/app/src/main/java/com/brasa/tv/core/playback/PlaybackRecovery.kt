package com.brasa.tv.core.playback

/** Uses elapsed time and position, not isPlaying: a stalled decoder can report playing. */
class PlaybackRecovery {
    private var lastPosition: Long? = null
    private var stalledSince: Long? = null
    private var stableSince: Long? = null
    private var lastPrematureEnd: Long? = null
    private var lastBufferedPosition: Long? = null
    private var bufferingSince: Long? = null
    var attempts: Int = 0
        private set
    private var sourceRenewals = 0
    fun beginSourceRenewal(): Boolean { if (sourceRenewals >= 1) return false; sourceRenewals++; return true }

    fun sample(nowMs: Long, positionMs: Long, expectedToAdvance: Boolean,
               buffering: Boolean = false, bufferedPositionMs: Long = positionMs): Boolean {
        val previous = lastPosition
        val previousBuffer = lastBufferedPosition
        lastPosition = positionMs
        lastBufferedPosition = bufferedPositionMs
        if (!expectedToAdvance || previous == null || positionMs < previous) {
            stalledSince = null
            stableSince = null
            bufferingSince = null
            return false
        }
        if (buffering) {
            stableSince = null
            if (bufferingSince == null) bufferingSince = nowMs
            // Let Media3 finish rebuffering while downloads are progressing. Bound even
            // a trickling connection so a broken stream cannot wait forever.
            if (stalledSince == null || (previousBuffer != null && bufferedPositionMs > previousBuffer + 250)) stalledSince = nowMs
            return nowMs - stalledSince!! >= 45_000 || nowMs - bufferingSince!! >= 120_000
        }
        if (bufferingSince != null) stalledSince = null
        bufferingSince = null
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

    fun resetSampling() { lastPosition = null; stalledSince = null; stableSince = null; lastBufferedPosition = null; bufferingSince = null }

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

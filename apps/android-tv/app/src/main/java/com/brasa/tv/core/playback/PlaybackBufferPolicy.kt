package com.brasa.tv.core.playback

/** Keep a network cushion without filling the TV's heap with compressed 4K samples. */
object PlaybackBufferPolicy {
    data class Profile(
        val minMs: Int, val maxMs: Int, val startMs: Int, val rebufferMs: Int,
        val targetBytes: Int, val backBufferMs: Int,
    )

    fun select(mode: String, bitrate: Long, height: Int, memoryClassMb: Int, lowRam: Boolean): Profile {
        val budgetMb = (memoryClassMb / 4).coerceIn(16, if (lowRam) 32 else 64)
        val heavy = bitrate >= 20_000_000L || height >= 2160
        return Profile(
            minMs = if (heavy || mode == "hls") 45_000 else 30_000,
            maxMs = if (heavy || mode == "hls") 120_000 else 90_000,
            startMs = if (mode == "hls") 8_000 else if (heavy) 5_000 else 3_000,
            rebufferMs = if (heavy || mode == "hls") 12_000 else 8_000,
            targetBytes = budgetMb * 1024 * 1024,
            backBufferMs = if (lowRam || heavy) 0 else 10_000,
        )
    }
}
